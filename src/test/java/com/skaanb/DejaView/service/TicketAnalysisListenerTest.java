package com.skaanb.DejaView.service;

import com.skaanb.DejaView.dto.AIAnalysisResponse;
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

import java.util.ArrayList;
import java.util.List;
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

    private static AIAnalysisResponse analysis(String description, String solution, List<String> tags) {
        AIAnalysisResponse response = new AIAnalysisResponse();
        response.setDescription(description);
        response.setSolution(solution);
        response.setTags(tags);
        return response;
    }

    @Test
    void testHandle_Success_AppendsSolutionAndMarksCompleted() {
        // Given
        ticketState.getSolutions().add("Önceki çözüm");
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.analyze("hata açıklaması"))
                .thenReturn(analysis("Veritabanı bağlantısı koptu", "Yeni AI çözümü", List.of("database")));

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması"));

        // Then
        verify(ticketMutationExecutor, times(2)).mutate(eq("ticket-1"), any()); // PROCESSING sonra COMPLETED
        assertEquals(TicketStatus.COMPLETED, ticketState.getStatus());
        assertEquals("Veritabanı bağlantısı koptu", ticketState.getAiGeneratedDescription());
        // Eski çözüm silinmemeli, yenisi eklenmeli
        assertEquals(2, ticketState.getSolutions().size());
        assertTrue(ticketState.getSolutions().contains("Önceki çözüm"));
        assertTrue(ticketState.getSolutions().contains("Yeni AI çözümü"));
    }

    @Test
    void testHandle_AiTags_MergedWithExistingInsteadOfOverwritten() {
        // Given: kullanıcı ticket'ı açarken kendi etiketini vermiş
        ticketState.setAiTags(new ArrayList<>(List.of("manual", "database")));
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.analyze(any()))
                .thenReturn(analysis("özet", "çözüm", List.of("database", "timeout")));

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması"));

        // Then: kullanıcının etiketi korunmalı, AI'ınki eklenmeli, tekrar eden ("database")
        // iki kez yazılmamalı
        assertEquals(List.of("manual", "database", "timeout"), ticketState.getAiTags());
    }

    @Test
    void testHandle_ProviderReturnsNoTags_KeepsExistingTags() {
        // Given: yapısal çıktı desteklemeyen bir sağlayıcı (bkz.
        // AiSummarizationService.analyze varsayılan implementasyonu) boş etiket listesi döner
        ticketState.setAiTags(new ArrayList<>(List.of("manual")));
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.analyze(any()))
                .thenReturn(analysis("özet", "çözüm", List.of()));

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması"));

        // Then: etiketler silinmemeli, olduğu gibi kalmalı
        assertEquals(List.of("manual"), ticketState.getAiTags());
        assertEquals(TicketStatus.COMPLETED, ticketState.getStatus());
    }

    @Test
    void testHandle_AiSummarizationException_MarksFailed() {
        // Given
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.analyze(any()))
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
