package com.skaanb.DejaView.service;

import com.skaanb.DejaView.dto.TicketAnalysisMessage;
import com.skaanb.DejaView.exception.AiSummarizationException;
import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.model.TicketStatus;
import com.skaanb.DejaView.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketAnalysisListenerTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private AiSummarizationService aiSummarizationService;

    @Mock
    private TicketMutationExecutor ticketMutationExecutor;

    private TicketAnalysisListener listener;

    private TicketDocument ticketState;

    @BeforeEach
    void setUp() {
        listener = new TicketAnalysisListener(ticketRepository, aiSummarizationService, ticketMutationExecutor);

        ticketState = new TicketDocument();
        ticketState.setId("ticket-1");

        // TicketMutationExecutor.mutate mock'landığı için, gerçek repository/optimistic
        // locking davranışını değil, listener'ın mutator olarak neyi uyguladığını
        // doğruluyoruz: verilen Consumer'ı gerçek bir TicketDocument üzerinde çalıştırıyoruz.
        lenient().when(ticketMutationExecutor.mutate(eq("ticket-1"), any())).thenAnswer(invocation -> {
            Consumer<TicketDocument> mutator = invocation.getArgument(1);
            mutator.accept(ticketState);
            return ticketState;
        });
    }

    @Test
    void testHandle_Success_AppendsSolutionAndMarksCompleted() {
        // Given
        ticketState.getSolutions().add("Önceki çözüm");
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.summarize("hata açıklaması")).thenReturn("Yeni AI çözümü");

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması"));

        // Then
        verify(ticketMutationExecutor, times(2)).mutate(eq("ticket-1"), any()); // PROCESSING sonra COMPLETED
        assertEquals(TicketStatus.COMPLETED, ticketState.getStatus());
        assertEquals("Yeni AI çözümü", ticketState.getAiGeneratedDescription());
        // Eski çözüm silinmemeli, yenisi eklenmeli
        assertEquals(2, ticketState.getSolutions().size());
        assertTrue(ticketState.getSolutions().contains("Önceki çözüm"));
        assertTrue(ticketState.getSolutions().contains("Yeni AI çözümü"));
    }

    @Test
    void testHandle_AiSummarizationException_MarksFailed() {
        // Given
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.summarize(any()))
                .thenThrow(new AiSummarizationException("Gemini ile özet oluşturulamadı.", new RuntimeException("timeout")));

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması"));

        // Then
        verify(ticketMutationExecutor, times(2)).mutate(eq("ticket-1"), any()); // PROCESSING sonra FAILED
        assertEquals(TicketStatus.FAILED, ticketState.getStatus());
        assertTrue(ticketState.getSolutions().isEmpty());
    }

    @Test
    void testHandle_TicketNotFound_DoesNothing() {
        // Given
        when(ticketRepository.findById("missing")).thenReturn(Optional.empty());

        // When
        listener.handle(new TicketAnalysisMessage("missing", "açıklama"));

        // Then
        verify(ticketMutationExecutor, never()).mutate(any(), any());
        verifyNoInteractions(aiSummarizationService);
    }
}
