package com.skaanb.DejaView.service;

import com.skaanb.DejaView.config.RabbitConfig;
import com.skaanb.DejaView.dto.AIAnalysisResponse;
import com.skaanb.DejaView.dto.TicketAnalysisMessage;
import com.skaanb.DejaView.exception.AiSummarizationException;
import com.skaanb.DejaView.model.TicketStatus;
import com.skaanb.DejaView.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// RabbitMQ kuyruğundan ticket analiz isteklerini tüketir, AiSummarizationService'ten
// (Dependency Inversion — somut Gemini/OpenAI'a değil bu arayüze bağımlı, bkz.
// AiSummarizationService) özet/çözüm alır ve sonucu ilgili TicketDocument'a ekler
// (üzerine yazmaz, biriktirir). application.properties'teki
// spring.rabbitmq.listener.simple.concurrency ayarı sayesinde birden fazla thread bu
// metodu aynı anda çalıştırabilir; aynı ticket'a concurrent yazmalar
// TicketMutationExecutor ile optimistic locking + retry kullanılarak güvenli birleştirilir.
@Component
public class TicketAnalysisListener {

    private static final Logger logger = LoggerFactory.getLogger(TicketAnalysisListener.class);

    private final TicketRepository ticketRepository;
    private final AiSummarizationService aiSummarizationService;
    private final TicketMutationExecutor ticketMutationExecutor;

    public TicketAnalysisListener(TicketRepository ticketRepository,
                                   AiSummarizationService aiSummarizationService,
                                   TicketMutationExecutor ticketMutationExecutor) {
        this.ticketRepository = ticketRepository;
        this.aiSummarizationService = aiSummarizationService;
        this.ticketMutationExecutor = ticketMutationExecutor;
    }

    @RabbitListener(queues = RabbitConfig.TICKET_ANALYSIS_QUEUE)
    public void handle(TicketAnalysisMessage message) {
        String ticketId = message.getTicketId();
        logger.info("Ticket AI analizi başlıyor. ticketId={}", ticketId);

        if (ticketRepository.findById(ticketId).isEmpty()) {
            logger.warn("AI analizi için ticket bulunamadı (silinmiş olabilir). ticketId={}", ticketId);
            return;
        }

        ticketMutationExecutor.mutate(ticketId, ticket -> ticket.setStatus(TicketStatus.PROCESSING));

        try {
            // summarize() yerine analyze(): özet ve etiketler tek çağrıda geliyor
            // (bkz. AiSummarizationService.analyze).
            AIAnalysisResponse analysis = aiSummarizationService.analyze(message.getDescription());

            ticketMutationExecutor.mutate(ticketId, ticket -> {
                ticket.setAiGeneratedDescription(analysis.getDescription());
                ticket.getSolutions().add(analysis.getSolution());
                // Etiketler var olanların ÜZERİNE yazılmıyor, birleştiriliyor: kullanıcının
                // ticket'ı açarken elle verdiği etiketler kaybolmamalı (bkz. mergeAiTags).
                // Yapısal çıktı desteklemeyen bir sağlayıcı aktifse liste boş gelir ve
                // etiketler olduğu gibi kalır — bu beklenen bir durum, hata değil.
                ticket.mergeAiTags(analysis.getTags());
                ticket.setStatus(TicketStatus.COMPLETED);
            });

            logger.info("Ticket AI analizi tamamlandı. ticketId={}, etiketler={}", ticketId, analysis.getTags());
        } catch (AiSummarizationException e) {
            logger.error("Ticket AI analizi başarısız oldu. ticketId={}, hata={}", ticketId, e.getMessage(), e);
            ticketMutationExecutor.mutate(ticketId, ticket -> ticket.setStatus(TicketStatus.FAILED));
        }
    }
}
