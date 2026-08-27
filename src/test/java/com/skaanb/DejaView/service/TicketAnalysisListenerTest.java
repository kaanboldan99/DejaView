package com.skaanb.DejaView.service;

import com.skaanb.DejaView.dto.TicketAnalysisMessage;
import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.model.TicketStatus;
import com.skaanb.DejaView.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketAnalysisListenerTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private GeminiService geminiService;

    private TicketAnalysisListener listener;

    @BeforeEach
    void setUp() {
        listener = new TicketAnalysisListener(ticketRepository, geminiService);
    }

    @Test
    void testHandle_Success_AppendsSolutionAndMarksCompleted() {
        // Given
        TicketDocument ticket = new TicketDocument();
        ticket.setId("ticket-1");
        ticket.getSolutions().add("Önceki çözüm");
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(geminiService.summarize("hata açıklaması")).thenReturn("Yeni AI çözümü");

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması"));

        // Then
        ArgumentCaptor<TicketDocument> captor = ArgumentCaptor.forClass(TicketDocument.class);
        verify(ticketRepository, times(2)).save(captor.capture()); // PROCESSING sonra COMPLETED

        TicketDocument finalState = captor.getValue();
        assertEquals(TicketStatus.COMPLETED, finalState.getStatus());
        assertEquals("Yeni AI çözümü", finalState.getAiGeneratedDescription());
        // Eski çözüm silinmemeli, yenisi eklenmeli
        assertEquals(2, finalState.getSolutions().size());
        assertTrue(finalState.getSolutions().contains("Önceki çözüm"));
        assertTrue(finalState.getSolutions().contains("Yeni AI çözümü"));
    }

    @Test
    void testHandle_GeminiFailureMessage_MarksFailed() {
        // Given
        TicketDocument ticket = new TicketDocument();
        ticket.setId("ticket-1");
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(geminiService.summarize(any())).thenReturn(GeminiService.SUMMARY_FAILED_MESSAGE);

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması"));

        // Then
        ArgumentCaptor<TicketDocument> captor = ArgumentCaptor.forClass(TicketDocument.class);
        verify(ticketRepository, times(2)).save(captor.capture());
        assertEquals(TicketStatus.FAILED, captor.getValue().getStatus());
        assertTrue(captor.getValue().getSolutions().isEmpty());
    }

    @Test
    void testHandle_TicketNotFound_DoesNothing() {
        // Given
        when(ticketRepository.findById("missing")).thenReturn(Optional.empty());

        // When
        listener.handle(new TicketAnalysisMessage("missing", "açıklama"));

        // Then
        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(geminiService);
    }

    @Test
    void testHandle_UnexpectedException_MarksFailed() {
        // Given
        TicketDocument ticket = new TicketDocument();
        ticket.setId("ticket-1");
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(geminiService.summarize(any())).thenThrow(new RuntimeException("beklenmeyen hata"));

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "açıklama"));

        // Then
        ArgumentCaptor<TicketDocument> captor = ArgumentCaptor.forClass(TicketDocument.class);
        verify(ticketRepository, times(2)).save(captor.capture());
        assertEquals(TicketStatus.FAILED, captor.getValue().getStatus());
    }
}
