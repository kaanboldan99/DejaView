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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

    @Mock
    private TicketAnalysisProducer ticketAnalysisProducer;

    private TicketAnalysisListener listener;

    private TicketDocument ticketState;

    @BeforeEach
    void setUp() {
        listener = new TicketAnalysisListener(ticketRepository, aiSummarizationService, ticketMutationExecutor,
                ticketAnalysisProducer);

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
        return analysis(description, List.of(solution), tags);
    }

    private static AIAnalysisResponse analysis(String description, List<String> solutions, List<String> tags) {
        AIAnalysisResponse response = new AIAnalysisResponse();
        response.setDescription(description);
        response.setRootCause("kök neden");
        response.setSolutions(solutions);
        response.setTags(tags);
        return response;
    }

    @Test
    void testHandle_Success_AppendsSolutionAndMarksCompleted() {
        // Given
        ticketState.getSolutions().add("Önceki çözüm");
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.analyze("hata açıklaması", false))
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
        when(aiSummarizationService.analyze(any(), anyBoolean()))
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
        when(aiSummarizationService.analyze(any(), anyBoolean()))
                .thenReturn(analysis("özet", "çözüm", List.of()));

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması"));

        // Then: etiketler silinmemeli, olduğu gibi kalmalı
        assertEquals(List.of("manual"), ticketState.getAiTags());
        assertEquals(TicketStatus.COMPLETED, ticketState.getStatus());
    }

    @Test
    void testHandle_AiSummarizationException_SchedulesRetryInsteadOfDiscarding() {
        // Given: AI sağlayıcısı hata veriyor ve deneme hakkı var
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.analyze(any(), anyBoolean()))
                .thenThrow(new AiSummarizationException("Gemini ile özet oluşturulamadı.", new RuntimeException("timeout")));
        when(ticketAnalysisProducer.maxRetryAttempts()).thenReturn(3);
        when(ticketAnalysisProducer.scheduleRetry(any())).thenReturn(60);

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması"));

        // Then: mesaj kuyruktan düşürülmüyor, bir sonraki deneme numarasıyla bekletiliyor
        ArgumentCaptor<TicketAnalysisMessage> captor = ArgumentCaptor.forClass(TicketAnalysisMessage.class);
        verify(ticketAnalysisProducer).scheduleRetry(captor.capture());
        assertEquals(1, captor.getValue().getAttempt());
        assertEquals("ticket-1", captor.getValue().getTicketId());
        assertEquals("hata açıklaması", captor.getValue().getDescription());
        verify(ticketAnalysisProducer, never()).park(any(), any());

        // Ve kayıt FAILED değil PENDING: analiz vazgeçilmiş değil, kuyrukta bekliyor
        assertEquals(TicketStatus.PENDING, ticketState.getStatus());
        assertTrue(ticketState.getSolutions().isEmpty());
    }

    @Test
    void testHandle_AiSummarizationException_RetriesCarryRegenerateFlag() {
        // Given: "yeniden üret" isteği hata aldı
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.analyze(any(), anyBoolean()))
                .thenThrow(new AiSummarizationException("sağlayıcıya ulaşılamadı", new RuntimeException("connect")));
        when(ticketAnalysisProducer.maxRetryAttempts()).thenReturn(3);

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması", true));

        // Then: bekleyen mesaj isteğin kaynağını unutmamalı — yoksa yeniden deneme,
        // kullanıcının istediği "yeniden üret" yerine sıradan bir analize dönerdi
        ArgumentCaptor<TicketAnalysisMessage> captor = ArgumentCaptor.forClass(TicketAnalysisMessage.class);
        verify(ticketAnalysisProducer).scheduleRetry(captor.capture());
        assertTrue(captor.getValue().isRegenerate());
    }

    @Test
    void testHandle_AiSummarizationException_LastAttempt_ParksMessageAndMarksFailed() {
        // Given: son deneme de (attempt=3, hak=3) başarısız
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.analyze(any(), anyBoolean()))
                .thenThrow(new AiSummarizationException("Gemini ile özet oluşturulamadı.", new RuntimeException("timeout")));
        when(ticketAnalysisProducer.maxRetryAttempts()).thenReturn(3);

        TicketAnalysisMessage exhausted = new TicketAnalysisMessage("ticket-1", "hata açıklaması");
        exhausted.setAttempt(3);

        // When
        listener.handle(exhausted);

        // Then: artık bekletilmiyor ama SİLİNMİYOR da — park kuyruğuna alınıyor
        verify(ticketAnalysisProducer, never()).scheduleRetry(any());
        verify(ticketAnalysisProducer).park(eq(exhausted), any());
        assertEquals(TicketStatus.FAILED, ticketState.getStatus());
    }

    @Test
    void testHandle_RetryPublishFails_ExceptionPropagatesSoMessageIsNotAcked() {
        // Given: bekleme kuyruğuna yayın da başarısız (RabbitMQ'ya ulaşılamıyor)
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.analyze(any(), anyBoolean()))
                .thenThrow(new AiSummarizationException("sağlayıcıya ulaşılamadı", new RuntimeException("connect")));
        when(ticketAnalysisProducer.maxRetryAttempts()).thenReturn(3);
        when(ticketAnalysisProducer.scheduleRetry(any()))
                .thenThrow(new AmqpException("broker kapalı"));

        // When / Then: hata YUTULMAMALI. Yutulsaydı listener normal döner, RabbitMQ mesajı
        // ACK'ler ve istek hem bekleme kuyruğuna girmemiş hem de silinmiş olurdu.
        assertThrows(AmqpException.class,
                () -> listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması")));

        // Kayıt da "bekliyor" gibi görünmemeli; PROCESSING'de kalıp yeniden teslimatı bekler
        assertEquals(TicketStatus.PROCESSING, ticketState.getStatus());
    }

    @Test
    void testHandle_MultipleSolutions_AllAppended() {
        // Given: AI artık tek değil, birden fazla çözüm önerisi dönüyor
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.analyze(any(), anyBoolean()))
                .thenReturn(analysis("özet", List.of("çözüm A", "çözüm B", "çözüm C"), List.of("database")));

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması"));

        // Then: hepsi kayda yazılmalı, kök neden de dolmalı
        assertEquals(List.of("çözüm A", "çözüm B", "çözüm C"), ticketState.getSolutions());
        assertEquals("kök neden", ticketState.getAiRootCause());
    }

    @Test
    void testHandle_Regenerate_ReplacesSolutionsInsteadOfAppending() {
        // Given: kullanıcı "yeniden üret" dedi (regenerate=true) ve kayıtta eski öneriler var
        ticketState.getSolutions().add("Eski çözüm");
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.analyze("hata açıklaması", true))
                .thenReturn(analysis("özet", List.of("Yeni çözüm 1", "Yeni çözüm 2"), List.of("timeout")));

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması", true));

        // Then: eskiler birikmemeli, yenileri onların YERİNE geçmeli — aksi halde
        // butona her basış listeyi şişirirdi
        assertEquals(List.of("Yeni çözüm 1", "Yeni çözüm 2"), ticketState.getSolutions());
    }

    @Test
    void testHandle_DuplicateSolution_NotAppendedTwice() {
        // Given: aynı hata tekrar görüldü ve model aynı öneriyi yeniden üretti
        ticketState.getSolutions().add("Bağlantı havuzunu büyütün");
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticketState));
        when(aiSummarizationService.analyze(any(), anyBoolean()))
                .thenReturn(analysis("özet", List.of("Bağlantı havuzunu büyütün", "Yeni öneri"), List.of("db")));

        // When
        listener.handle(new TicketAnalysisMessage("ticket-1", "hata açıklaması"));

        // Then: aynı metin listede iki kez görünmemeli
        assertEquals(List.of("Bağlantı havuzunu büyütün", "Yeni öneri"), ticketState.getSolutions());
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
