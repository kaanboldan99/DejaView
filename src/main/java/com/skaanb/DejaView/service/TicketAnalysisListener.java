package com.skaanb.DejaView.service;

import com.skaanb.DejaView.config.RabbitConfig;
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
            String solution = aiSummarizationService.summarize(message.getDescription());

            ticketMutationExecutor.mutate(ticketId, ticket -> {
                ticket.setAiGeneratedDescription(solution);
                ticket.getSolutions().add(solution);
                ticket.setStatus(TicketStatus.COMPLETED);
            });

            logger.info("Ticket AI analizi tamamlandı. ticketId={}", ticketId);
        } catch (AiSummarizationException e) {
            logger.error("Ticket AI analizi başarısız oldu. ticketId={}, hata={}", ticketId, e.getMessage(), e);
            ticketMutationExecutor.mutate(ticketId, ticket -> ticket.setStatus(TicketStatus.FAILED));
        }
    }
}
