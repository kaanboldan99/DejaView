package com.skaanb.DejaView.service;

import com.skaanb.DejaView.dto.CreateTicketRequest;
import com.skaanb.DejaView.dto.TicketResponse;
import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// TicketRepository Elasticsearch'e bağlandığı için burada mock'lanıyor;
// gerçek bir ES kümesi olmadan da testler güvenilir şekilde çalışsın diye
// @SpringBootTest yerine saf Mockito unit test tercih edildi.
@ExtendWith(MockitoExtension.class)
public class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository);
    }

    @Test
    void testCreateTicket_Success() {
        // Given
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("Bağlantı Hatası");
        request.setDescription("PostgreSQL sunucusuna bağlanılamadı, port 5432 refused.");
        request.setTags(List.of("db"));

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
        assertEquals("PostgreSQL sunucusuna bağlanılamadı, port 5432 refused.", response.getErrorMessage());
        assertEquals(List.of("db"), response.getAiTags());
        assertEquals("kaanboldan", response.getCreatedBy());

        ArgumentCaptor<TicketDocument> captor = ArgumentCaptor.forClass(TicketDocument.class);
        verify(ticketRepository).save(captor.capture());
        assertEquals("kaanboldan", captor.getValue().getCreatedBy());
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
        ticketService.deleteTicket("ticket-1", "kaanboldan");

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
        // Farklı bir kullanıcı silmeye çalışırsa RuntimeException fırlatmasını bekliyoruz
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                ticketService.deleteTicket("ticket-1", "farkli_kullanici"));

        assertEquals("Bu ticket'ı silme yetkiniz yok", exception.getMessage());
        verify(ticketRepository, never()).deleteById(anyString());
    }

    @Test
    void testDeleteTicket_NotFound_ThrowsException() {
        // Given
        when(ticketRepository.findById("missing")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> ticketService.deleteTicket("missing", "kaanboldan"));
        verify(ticketRepository, never()).deleteById(anyString());
    }
}
