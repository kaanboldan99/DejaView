package com.skaanb.DejaView.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.skaanb.DejaView.dto.CreateTicketRequest;
import com.skaanb.DejaView.dto.TicketResponse;
import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.model.TicketStatus;
import com.skaanb.DejaView.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class TicketService {

    /** Elle açılan ve servisi belirtilmeyen kayıtlar için varsayılan servis adı. */
    private static final String DEFAULT_SERVICE_NAME = "Manuel Kayıt";

    /** serviceName Keyword alanı; aşırı uzun değerler filtre listesini bozar. */
    private static final int MAX_SERVICE_NAME_LENGTH = 64;

    private static final Logger logger = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final TicketAnalysisProducer ticketAnalysisProducer;
    private final TicketMutationExecutor ticketMutationExecutor;

    @Autowired
    public TicketService(TicketRepository ticketRepository,
                          ElasticsearchOperations elasticsearchOperations,
                          TicketAnalysisProducer ticketAnalysisProducer,
                          TicketMutationExecutor ticketMutationExecutor) {
        this.ticketRepository = ticketRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.ticketAnalysisProducer = ticketAnalysisProducer;
        this.ticketMutationExecutor = ticketMutationExecutor;
    }

    // GlobalExceptionHandler için yapay zekasız kayıt metodu
    public TicketDocument saveTicketLog(String errorMessage, String stackTrace, String serviceName) {
        TicketDocument ticket = new TicketDocument();
        ticket.setErrorMessage(errorMessage);
        ticket.setStackTrace(stackTrace);
        ticket.setServiceName(serviceName);
        ticket.setCreatedAt(Instant.now());
        ticket.setLastOccurrenceAt(Instant.now());

        ticket.setAiGeneratedDescription("AI analizi devre dışı bırakıldı.");
        ticket.setAiTags(List.of("Log"));
        ticket.getSolutions().add("Lokal log kayıtları incelenmelidir.");
        ticket.setCreatedBy("system");
        ticket.setStatus(TicketStatus.COMPLETED);

        TicketDocument saved = ticketRepository.save(ticket);
        logger.info("Sistem hatası ticket olarak kaydedildi. id={}, servis={}", saved.getId(), serviceName);
        return saved;
    }

    // Controller katmanından gelen manuel bilet oluşturma isteği.
    // Aynı başlıkla (büyük/küçük harf duyarsız) daha önce açılmış bir kayıt varsa
    // yeni kayıt açmak yerine mevcut kayda "yeni bir görülme" olarak işlenir: başlık
    // değişmez, occurrence sayısı artar, tag'ler birleştirilir. Doküman ID'si başlıktan
    // deterministik olarak türetilir (bkz. computeTicketId) — böylece aynı başlık için
    // iki AYRI doküman oluşması yapısal olarak engellenir; concurrent güncellemeler
    // TicketMutationExecutor ile optimistic locking + retry kullanılarak güvenli şekilde
    // birleştirilir (bkz. TicketDocument@Version). AI analizi her iki durumda da RabbitMQ
    // üzerinden arka planda çalışır; sonuç geldiğinde solutions listesine eklenir.
    //
    // Bilinen sınır: iki isteğin TAM OLARAK aynı anda, o başlık için ilk kez ticket
    // oluşturmaya çalıştığı (yani ikisi de "mevcut değil" görüp yeni doküman yazmaya
    // çalıştığı) çok nadir durumda, ES tarafında gerçek "create-only" (op_type=create)
    // ataomikliği kullanılmadığı için ikinci yazma ilkini ezebilir. Pratikte ihmal
    // edilebilir bir risk (aynı milisaniyede aynı başlıkla ilk kayıt); tespit edilirse
    // ElasticsearchOperations üzerinden IndexQuery.OpType.CREATE ile sıkılaştırılabilir.
    public TicketResponse createTicket(CreateTicketRequest request, String username) {
        String title = (request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle().trim()
                : "Başlıksız Kayıt";
        String titleNormalized = title.toLowerCase();
        String description = request.getDescription() != null ? request.getDescription() : "Manuel Kayıt";
        List<String> requestedTags = request.getTags() != null ? request.getTags() : List.of("Manual");
        String serviceName = resolveServiceName(request.getServiceName());
        String ticketId = computeTicketId(titleNormalized);

        TicketDocument saved;
        if (ticketRepository.existsById(ticketId)) {
            saved = ticketMutationExecutor.mutate(ticketId, ticket -> {
                ticket.setOccurrenceCount(ticket.getOccurrenceCount() + 1);
                ticket.setLastOccurrenceAt(Instant.now());
                ticket.mergeAiTags(requestedTags);
                ticket.setStatus(TicketStatus.PENDING);
            });
            logger.info("Aynı başlıkla mevcut ticket bulundu, occurrence artırıldı. id={}, title='{}', occurrenceCount={}",
                    saved.getId(), title, saved.getOccurrenceCount());
        } else {
            TicketDocument ticket = new TicketDocument();
            ticket.setId(ticketId);
            ticket.setTitle(title);
            ticket.setTitleNormalized(titleNormalized);
            ticket.setErrorMessage(description);
            ticket.setStackTrace("Kullanıcı tarafından manuel oluşturuldu. Oluşturan: " + username);
            ticket.setServiceName(serviceName);
            ticket.setCreatedAt(Instant.now());
            ticket.setLastOccurrenceAt(Instant.now());
            ticket.setAiGeneratedDescription("AI analizi bekleniyor...");
            ticket.setAiTags(requestedTags);
            ticket.setCreatedBy(username);
            ticket.setOccurrenceCount(1);
            ticket.setStatus(TicketStatus.PENDING);

            saved = ticketRepository.save(ticket);
            logger.info("Ticket oluşturuldu. id={}, title='{}', oluşturan={}", saved.getId(), title, username);
        }

        ticketAnalysisProducer.enqueueAnalysis(saved.getId(), description);

        return TicketResponse.fromTicket(saved);
    }

    // Aynı normalize edilmiş başlık her zaman aynı hash'i (dolayısıyla aynı doküman ID'sini)
    // üretir. Bu, "aynı başlık = aynı kayıt" kuralını sorgu bazlı aramaya değil,
    // dokümanın kimliğine bağlar; başlık bir daha asla değişmeyeceği için stabildir.
    /**
     * İstemciden gelen servis adını temizler.
     *
     * serviceName Elasticsearch'te Keyword; yani birebir eşleşmeyle filtrelenir.
     * Bu yüzden baştaki/sondaki boşluklar kırpılır ve makul bir uzunlukla
     * sınırlanır — aksi halde "odeme" ile "odeme " ayrı iki servis gibi görünür.
     *
     * Boş gelirse kaydın elle açıldığını belirten varsayılan kullanılır.
     * (Önceden burada sabit "TicketController" yazılıydı; bu bir servis adı
     * değil, controller sınıfının adıydı.)
     */
    private String resolveServiceName(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SERVICE_NAME;
        }
        String trimmed = raw.trim();
        return trimmed.length() > MAX_SERVICE_NAME_LENGTH
                ? trimmed.substring(0, MAX_SERVICE_NAME_LENGTH)
                : trimmed;
    }

    private String computeTicketId(String titleNormalized) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(titleNormalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 her JVM'de garanti mevcuttur; pratikte hiç tetiklenmez.
            throw new IllegalStateException("SHA-256 algoritması bulunamadı", e);
        }
    }

    public Optional<TicketDocument> getTicketById(String id) {
        return ticketRepository.findById(id);
    }

    public List<TicketDocument> getAllTickets() {
        return StreamSupport.stream(ticketRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());
    }

    public void deleteTicket(String id, String username, boolean isAdmin) {
        TicketDocument ticket = ticketRepository.findById(id)
                .orElseThrow(() -> {
                    logger.warn("Silinmek istenen ticket bulunamadı. id={}, istekte bulunan={}", id, username);
                    return new RuntimeException("Ticket bulunamadı");
                });

        if (!isAdmin && !username.equals(ticket.getCreatedBy())) {
            logger.warn("Yetkisiz ticket silme denemesi. id={}, sahibi={}, istekte bulunan={}",
                    id, ticket.getCreatedBy(), username);
            throw new RuntimeException("Bu ticket'ı silme yetkiniz yok");
        }

        ticketRepository.deleteById(id);
        logger.info("Ticket silindi. id={}, silen={}, admin={}", id, username, isAdmin);
    }

    // Admin: mevcut bir ticket için AI analizini yeniden tetikler. Sonuç, mevcut
    // solutions listesine yeni bir giriş olarak eklenir (var olanlar korunur).
    public TicketResponse resummarizeTicket(String id) {
        TicketDocument saved = ticketMutationExecutor.mutate(id, ticket -> ticket.setStatus(TicketStatus.PENDING));

        ticketAnalysisProducer.enqueueAnalysis(saved.getId(), saved.getErrorMessage());
        logger.info("Ticket için AI analizi yeniden kuyruğa alındı (admin). id={}", id);

        return TicketResponse.fromTicket(saved);
    }

    // query parametresine göre errorMessage, serviceName ve aiTags alanlarında
    // büyük/küçük harf duyarsız arama yapar. Eskiden tüm index Java tarafına
    // çekilip String.contains ile filtreleniyordu; artık eşleştirmeyi
    // Elasticsearch'in kendisi yapıyor (index büyüdükçe ölçeklenir).
    public List<TicketResponse> searchTickets(String query) {
        logger.debug("Ticket araması yapılıyor. query='{}'", query);
        if (query == null || query.isBlank()) {
            return getAllTickets().stream()
                    .map(TicketResponse::fromTicket)
                    .collect(Collectors.toList());
        }

        String pattern = "*" + query.toLowerCase() + "*";

        Query esQuery = Query.of(q -> q.bool(b -> b
                .should(s -> s.wildcard(w -> w.field("errorMessage").value(pattern).caseInsensitive(true)))
                .should(s -> s.wildcard(w -> w.field("serviceName").value(pattern).caseInsensitive(true)))
                .should(s -> s.wildcard(w -> w.field("aiTags").value(pattern).caseInsensitive(true)))
                .minimumShouldMatch("1")
        ));

        NativeQuery nativeQuery = NativeQuery.builder().withQuery(esQuery).build();
        SearchHits<TicketDocument> hits = elasticsearchOperations.search(nativeQuery, TicketDocument.class);

        return hits.stream()
                .map(hit -> TicketResponse.fromTicket(hit.getContent()))
                .collect(Collectors.toList());
    }
}
