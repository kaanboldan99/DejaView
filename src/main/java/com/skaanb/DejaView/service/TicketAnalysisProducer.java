package com.skaanb.DejaView.service;

import com.skaanb.DejaView.config.RabbitConfig;
import com.skaanb.DejaView.dto.TicketAnalysisMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

// Yeni/tekrar eden bir ticket'ın AI analizini RabbitMQ kuyruğuna atar.
// Gerçek analiz TicketAnalysisListener tarafında, arka planda yapılır.
@Service
public class TicketAnalysisProducer {

    private static final Logger logger = LoggerFactory.getLogger(TicketAnalysisProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public TicketAnalysisProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void enqueueAnalysis(String ticketId, String description) {
        try {
            TicketAnalysisMessage message = new TicketAnalysisMessage(ticketId, description);
            rabbitTemplate.convertAndSend(
                    RabbitConfig.TICKET_ANALYSIS_EXCHANGE,
                    RabbitConfig.TICKET_ANALYSIS_ROUTING_KEY,
                    message
            );
            logger.info("Ticket AI analiz kuyruğuna eklendi. ticketId={}", ticketId);
        } catch (Exception e) {
            // RabbitMQ'ya ulaşılamıyorsa ticket zaten PENDING durumunda kaydedilmiş
            // olur; kullanıcı isteği bu yüzden başarısız olmamalı, sadece loglanır.
            logger.error("Ticket kuyruğa eklenemedi (RabbitMQ'ya ulaşılamıyor olabilir). ticketId={}, hata={}",
                    ticketId, e.getMessage(), e);
        }
    }
}
