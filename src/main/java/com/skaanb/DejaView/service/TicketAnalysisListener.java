package com.skaanb.DejaView.service;

import com.skaanb.DejaView.config.RabbitConfig;
import com.skaanb.DejaView.dto.TicketAnalysisMessage;
import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.model.TicketStatus;
import com.skaanb.DejaView.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

// RabbitMQ kuyruğundan ticket analiz isteklerini tüketir, Gemini'den özet/çözüm
// alır ve sonucu ilgili TicketDocument'a ekler (üzerine yazmaz, biriktirir).
@Component
public class TicketAnalysisListener {

    private static final Logger logger = LoggerFactory.getLogger(TicketAnalysisListener.class);

    private final TicketRepository ticketRepository;
    private final GeminiService geminiService;

    public TicketAnalysisListener(TicketRepository ticketRepository, GeminiService geminiService) {
        this.ticketRepository = ticketRepository;
        this.geminiService = geminiService;
    }

    @RabbitListener(queues = RabbitConfig.TICKET_ANALYSIS_QUEUE)
    public void handle(TicketAnalysisMessage message) {
        String ticketId = message.getTicketId();
        logger.info("Ticket AI analizi başlıyor. ticketId={}", ticketId);

        TicketDocument ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) {
            logger.warn("AI analizi için ticket bulunamadı (silinmiş olabilir). ticketId={}", ticketId);
            return;
        }

        ticket.setStatus(TicketStatus.PROCESSING);
        ticketRepository.save(ticket);

        try {
            String solution = geminiService.summarize(message.getDescription());

            if (GeminiService.SUMMARY_FAILED_MESSAGE.equals(solution)) {
                // GeminiService kendi içinde exception yutup fallback metin döner;
                // bunu gerçek bir başarısızlık olarak işaretliyoruz.
                ticket.setStatus(TicketStatus.FAILED);
                ticketRepository.save(ticket);
                logger.warn("Ticket AI analizi başarısız işaretlendi. ticketId={}", ticketId);
                return;
            }

            ticket.setAiGeneratedDescription(solution);
            ticket.getSolutions().add(solution);
            ticket.setStatus(TicketStatus.COMPLETED);
            ticketRepository.save(ticket);

            logger.info("Ticket AI analizi tamamlandı. ticketId={}", ticketId);
        } catch (Exception e) {
            logger.error("Ticket AI analizi başarısız oldu. ticketId={}, hata={}", ticketId, e.getMessage(), e);
            ticket.setStatus(TicketStatus.FAILED);
            ticketRepository.save(ticket);
        }
    }
}
