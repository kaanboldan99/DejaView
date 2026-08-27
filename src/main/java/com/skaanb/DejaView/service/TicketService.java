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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class TicketService {

    private static final Logger logger = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final TicketAnalysisProducer ticketAnalysisProducer;

    @Autowired
    public TicketService(TicketRepository ticketRepository,
                          ElasticsearchOperations elasticsearchOperations,
                          TicketAnalysisProducer ticketAnalysisProducer) {
        this.ticketRepository = ticketRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.ticketAnalysisProducer = ticketAnalysisProducer;
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
    // yeni kayıt açmak yerine mevcut kayda "yeni bir görülme" olarak işlenir:
    // başlık değişmez, occurrence sayısı artar, tag'ler birleştirilir. AI analizi
    // her iki durumda da RabbitMQ üzerinden arka planda çalışır; sonuç geldiğinde
    // solutions listesine eklenir (üzerine yazılmaz).
    public TicketResponse createTicket(CreateTicketRequest request, String username) {
        String title = (request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle().trim()
                : "Başlıksız Kayıt";
        String titleNormalized = title.toLowerCase();
        String description = request.getDescription() != null ? request.getDescription() : "Manuel Kayıt";
        List<String> requestedTags = request.getTags() != null ? request.getTags() : List.of("Manual");

        Optional<TicketDocument> existing = ticketRepository.findByTitleNormalized(titleNormalized);

        TicketDocument saved;
        if (existing.isPresent()) {
            TicketDocument ticket = existing.get();
            ticket.setOccurrenceCount(ticket.getOccurrenceCount() + 1);
            ticket.setLastOccurrenceAt(Instant.now());
            ticket.setAiTags(mergeTags(ticket.getAiTags(), requestedTags));
            ticket.setStatus(TicketStatus.PENDING);

            saved = ticketRepository.save(ticket);
            logger.info("Aynı başlıkla mevcut ticket bulundu, occurrence artırıldı. id={}, title='{}', occurrenceCount={}",
                    saved.getId(), title, saved.getOccurrenceCount());
        } else {
            TicketDocument ticket = new TicketDocument();
            ticket.setTitle(title);
            ticket.setTitleNormalized(titleNormalized);
            ticket.setErrorMessage(description);
            ticket.setStackTrace("Kullanıcı tarafından manuel oluşturuldu. Oluşturan: " + username);
            ticket.setServiceName("TicketController");
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

    private List<String> mergeTags(List<String> existingTags, List<String> newTags) {
        Set<String> merged = new LinkedHashSet<>();
        if (existingTags != null) {
            merged.addAll(existingTags);
        }
        if (newTags != null) {
            merged.addAll(newTags);
        }
        return new ArrayList<>(merged);
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
        TicketDocument ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket bulunamadı"));

        ticket.setStatus(TicketStatus.PENDING);
        TicketDocument saved = ticketRepository.save(ticket);

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
