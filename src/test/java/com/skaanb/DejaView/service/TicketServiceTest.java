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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, elasticsearchOperations, ticketAnalysisProducer);
    }

    @Test
    void testCreateTicket_NewTitle_Success() {
        // Given
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Bağlantı Hatası");
        request.setDescription("PostgreSQL sunucusuna bağlanılamadı, port 5432 refused.");
        request.setTags(List.of("db"));

        when(ticketRepository.findByTitleNormalized("bağlantı hatası")).thenReturn(Optional.empty());
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(invocation -> {
            TicketDocument saved = invocation.getArgument(0);
            saved.setId("generated-id");
            return saved;
        });

        // When
        TicketResponse response = ticketService.createTicket(request, "kaanboldan");

        // Then
        assertNotNull(response);
        assertEquals("generated-id", response.getId());
        assertEquals("Bağlantı Hatası", response.getTitle());
        assertEquals("PostgreSQL sunucusuna bağlanılamadı, port 5432 refused.", response.getErrorMessage());
        assertEquals(List.of("db"), response.getAiTags());
        assertEquals("kaanboldan", response.getCreatedBy());
        assertEquals(1, response.getOccurrenceCount());
        assertEquals(TicketStatus.PENDING, response.getStatus());

        ArgumentCaptor<TicketDocument> captor = ArgumentCaptor.forClass(TicketDocument.class);
        verify(ticketRepository).save(captor.capture());
        assertEquals("bağlantı hatası", captor.getValue().getTitleNormalized());

        // AI analizi kuyruğa gönderilmeli
        verify(ticketAnalysisProducer).enqueueAnalysis(eq("generated-id"), anyString());
    }

    @Test
    void testCreateTicket_DuplicateTitle_MergesTagsAndIncrementsOccurrence() {
        // Given: aynı başlıkla daha önce açılmış bir ticket var
        TicketDocument existing = new TicketDocument();
        existing.setId("ticket-1");
        existing.setTitle("Bağlantı Hatası");
        existing.setTitleNormalized("bağlantı hatası");
        existing.setAiTags(List.of("db"));
        existing.setOccurrenceCount(1);
        existing.setCreatedBy("ilkKullanici");

        when(ticketRepository.findByTitleNormalized("bağlantı hatası")).thenReturn(Optional.of(existing));
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Bağlantı Hatası");
        request.setDescription("Aynı hata tekrar oluştu.");
        request.setTags(List.of("timeout"));

        // When
        TicketResponse response = ticketService.createTicket(request, "farkliKullanici");

        // Then: yeni bir ticket oluşturulmuyor, mevcut kayıt güncelleniyor
        assertEquals("ticket-1", response.getId());
        assertEquals("Bağlantı Hatası", response.getTitle()); // başlık değişmedi
        assertEquals("ilkKullanici", response.getCreatedBy()); // orijinal sahibi değişmedi
        assertEquals(2, response.getOccurrenceCount()); // arttı
        assertEquals(List.of("db", "timeout"), response.getAiTags()); // birleşti
        assertEquals(TicketStatus.PENDING, response.getStatus());

        verify(ticketRepository, times(1)).save(any(TicketDocument.class));
        verify(ticketAnalysisProducer).enqueueAnalysis(eq("ticket-1"), anyString());
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

    @Test
    void testResummarizeTicket_EnqueuesAnalysis() {
        // Given
        TicketDocument existing = new TicketDocument();
        existing.setId("ticket-1");
        existing.setErrorMessage("Orijinal hata mesajı");
        existing.setStatus(TicketStatus.COMPLETED);
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(existing));
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
}
