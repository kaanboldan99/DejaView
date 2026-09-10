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

/**
 * Kayıt iş kuralları: oluşturma, tekilleştirme, arama, silme ve yeniden analiz.
 *
 * Nasıl çalışır: sınıfın taşıdığı en önemli karar TEKİLLEŞTİRMEDİR — aynı
 * başlıkla ikinci kez kayıt açılmaz, mevcut kayda "yeni bir görülme" olarak
 * işlenir. Bu kural sorgu bazlı aramaya değil, dokümanın KİMLİĞİNE bağlanmıştır:
 * doküman kimliği normalize edilmiş başlığın SHA-256 özetidir
 * (bkz. {@link #computeTicketId(String)}), yani aynı başlık her zaman aynı
 * kimliği verir ve iki ayrı doküman oluşması yapısal olarak engellenir.
 *
 * AI analizi hiçbir yolda burada çalışmaz; her zaman RabbitMQ üzerinden arka
 * plana devredilir (bkz. {@link TicketAnalysisProducer}), böylece kullanıcı
 * modelin yanıtını beklemez.
 */
@Service
public class TicketService {

    /** Elle açılan ve servisi belirtilmeyen kayıtlar için varsayılan servis adı. */
    private static final String DEFAULT_SERVICE_NAME = "Manuel Kayıt";

    /** serviceName Keyword alanı; aşırı uzun değerler filtre listesini bozar. */
    private static final int MAX_SERVICE_NAME_LENGTH = 64;

    private static final Logger logger = LoggerFactory.getLogger(TicketService.class);

    /** Kayıt okuma/yazma deposu. */
    private final TicketRepository ticketRepository;

    /** Elle yazılmış arama sorgularını çalıştıran şablon. */
    private final ElasticsearchOperations elasticsearchOperations;

    /** Analiz isteklerini kuyruğa koyan üretici. */
    private final TicketAnalysisProducer ticketAnalysisProducer;

    /** Kayıt güncellemelerini çakışmaya karşı güvenli yürüten bileşen. */
    private final TicketMutationExecutor ticketMutationExecutor;

    /**
     * @param ticketRepository        kayıt deposu
     * @param elasticsearchOperations arama sorgularını çalıştıran şablon
     * @param ticketAnalysisProducer  analiz kuyruğu üreticisi
     * @param ticketMutationExecutor  çakışma güvenli güncelleme bileşeni
     */
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

    /**
     * Sistem hatasını AI analizine sokmadan doğrudan kayıt olarak yazar.
     *
     * Nasıl çalışır: {@link com.skaanb.DejaView.exception.GlobalExceptionHandler}
     * için tasarlandı. Kuyruğa hiç girilmediği için durum doğrudan
     * {@link TicketStatus#COMPLETED} yazılır ve AI alanlarına yer tutucu
     * metinler konur. Tekilleştirme de uygulanmaz — kimlik verilmediği için her
     * çağrı yeni bir doküman üretir.
     *
     * @param errorMessage hatanın metni
     * @param stackTrace   hatanın yığın izi
     * @param serviceName  hatanın geldiği servis adı
     * @return kaydedilmiş doküman (kimliği atanmış hâlde)
     */
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

    /**
     * Yeni kayıt açar ya da aynı başlıklı mevcut kaydın görülme sayısını artırır.
     *
     * Nasıl çalışır: önce eksik alanlar varsayılanlara tamamlanır, başlık
     * normalize edilir ve ondan doküman kimliği türetilir. Sonra iki yoldan
     * biri işler:
     *
     * - Kimlik zaten varsa: başlık DEĞİŞMEZ, görülme sayacı artırılır, son
     *   görülme zamanı güncellenir, etiketler birleştirilir ve durum PENDING'e
     *   çekilir. Güncelleme {@link TicketMutationExecutor} üzerinden yapılır,
     *   yani eşzamanlı iki görülme birbirinin sayacını ezmez.
     *
     * - Kimlik yoksa: doküman sıfırdan kurulup kaydedilir.
     *
     * Her iki yolda da sonunda AI analizi kuyruğa konur; sonuç geldiğinde
     * çözüm listesine eklenir.
     *
     * Bilinen sınır: iki isteğin TAM OLARAK aynı anda, o başlık için ilk kez
     * kayıt oluşturmaya çalıştığı (yani ikisinin de "mevcut değil" görüp yeni
     * doküman yazmaya çalıştığı) çok nadir durumda, Elasticsearch tarafında
     * gerçek "yalnızca oluştur" ({@code op_type=create}) atomikliği
     * kullanılmadığı için ikinci yazma ilkini ezebilir. Pratikte ihmal
     * edilebilir bir risk; tespit edilirse {@code IndexQuery.OpType.CREATE} ile
     * sıkılaştırılabilir.
     *
     * @param request  kayıt gövdesi; alanları eksik olabilir, varsayılanlar uygulanır
     * @param username kaydı açan kullanıcı adı; yeni dokümanda sahibi olarak yazılır
     * @return oluşturulan ya da güncellenen kaydın yanıt hâli
     */
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

    /**
     * İstemciden gelen servis adını temizler.
     *
     * Nasıl çalışır: {@code serviceName} Elasticsearch'te Keyword tipindedir,
     * yani birebir eşleşmeyle filtrelenir. Bu yüzden baştaki/sondaki boşluklar
     * kırpılır ve makul bir uzunlukla sınırlanır — aksi halde {@code "odeme"}
     * ile {@code "odeme "} ayrı iki servis gibi görünürdü.
     *
     * Boş gelirse kaydın elle açıldığını belirten varsayılan kullanılır.
     * (Önceden burada sabit {@code "TicketController"} yazılıydı; bu bir servis
     * adı değil, controller sınıfının adıydı.)
     *
     * @param raw istemciden gelen ham servis adı; {@code null} olabilir
     * @return kırpılmış ve uzunluğu sınırlanmış ad; girdi boşsa varsayılan
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

    /**
     * Normalize edilmiş başlıktan deterministik doküman kimliği üretir.
     *
     * Nasıl çalışır: aynı normalize edilmiş başlık her zaman aynı SHA-256
     * özetini, dolayısıyla aynı doküman kimliğini üretir. Bu, "aynı başlık =
     * aynı kayıt" kuralını sorgu bazlı aramaya değil dokümanın kimliğine bağlar;
     * başlık bir daha asla değişmediği için de kimlik stabildir.
     *
     * @param titleNormalized kırpılmış ve küçük harfe çevrilmiş başlık
     * @return 64 karakterlik onaltılık özet; doküman kimliği olarak kullanılır
     * @throws IllegalStateException SHA-256 bulunamazsa (pratikte oluşmaz,
     *                               her JVM'de garanti mevcuttur)
     */
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
            throw new IllegalStateException("SHA-256 algoritması bulunamadı", e);
        }
    }

    /**
     * Kaydı doküman kimliğiyle bulur.
     *
     * @param id kaydın doküman kimliği
     * @return kayıt; yoksa boş {@link Optional}
     */
    public Optional<TicketDocument> getTicketById(String id) {
        return ticketRepository.findById(id);
    }

    /**
     * Tüm kayıtları döner.
     *
     * Nasıl çalışır: deponun döndürdüğü {@code Iterable} listeye çevrilir.
     * Sayfalama yoktur; indeks büyüdüğünde bu metodun yerini aramanın alması
     * beklenir.
     *
     * @return indeksteki tüm kayıtlar
     */
    public List<TicketDocument> getAllTickets() {
        return StreamSupport.stream(ticketRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());
    }

    /**
     * Kaydı siler; sahiplik kontrolünü uygular.
     *
     * Nasıl çalışır: kayıt önce okunur, sonra yetki denetlenir — ADMIN her
     * kaydı silebilir, diğer kullanıcılar yalnızca kendi açtıklarını. Yetkisiz
     * denemeler kimin neyi silmeye çalıştığıyla birlikte loglanır.
     *
     * @param id       silinecek kaydın doküman kimliği
     * @param username isteği yapan kullanıcı adı
     * @param isAdmin  isteği yapan ADMIN ise {@code true}; sahiplik kontrolünü atlar
     * @throws RuntimeException kayıt bulunamazsa ya da kullanıcının yetkisi yoksa
     */
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

    /**
     * ADMIN: mevcut bir kayıt için AI analizini yeniden tetikler.
     *
     * Nasıl çalışır: kaydın durumu PENDING'e çekilir ve istek
     * {@code regenerate=true} ile kuyruğa girer. Bunun iki sonucu var:
     * sağlayıcıdan aynı çıktının tekrarı yerine FARKLI bir bakış açısı istenir
     * ve gelen çözümler mevcut listeye EKLENMEK yerine onun yerine geçer
     * (bkz. {@link TicketAnalysisListener}). Aksi halde butona her basış
     * listeye birbirinin benzeri maddeler eklerdi.
     *
     * Etiketler yine birleştirilir, yani kullanıcının elle verdikleri korunur.
     *
     * @param id yeniden analiz edilecek kaydın doküman kimliği
     * @return kaydın kuyruğa alınmış (PENDING) hâli
     * @throws IllegalArgumentException kayıt bulunamazsa
     */
    public TicketResponse resummarizeTicket(String id) {
        TicketDocument saved = ticketMutationExecutor.mutate(id, ticket -> ticket.setStatus(TicketStatus.PENDING));

        ticketAnalysisProducer.enqueueAnalysis(saved.getId(), saved.getErrorMessage(), true);
        logger.info("Ticket için AI analizi yeniden kuyruğa alındı (admin). id={}", id);

        return TicketResponse.fromTicket(saved);
    }

    /**
     * Kayıtlarda metin araması yapar.
     *
     * Nasıl çalışır: sorgu boşsa tüm kayıtlar döner. Doluysa arama terimi
     * joker karakterlerle sarılıp üç alanda birden ({@code errorMessage},
     * {@code serviceName}, {@code aiTags}) büyük/küçük harf duyarsız aranır;
     * {@code minimumShouldMatch("1")} sayesinde alanlardan HERHANGİ BİRİNDE
     * eşleşme yeterlidir.
     *
     * Eskiden tüm indeks Java tarafına çekilip {@code String.contains} ile
     * filtreleniyordu; artık eşleştirmeyi Elasticsearch'in kendisi yapıyor,
     * yani indeks büyüdükçe ölçekleniyor.
     *
     * @param query aranacak metin; {@code null} veya boş olabilir
     * @return eşleşen kayıtların yanıt hâli; sorgu boşsa tüm kayıtlar
     */
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
