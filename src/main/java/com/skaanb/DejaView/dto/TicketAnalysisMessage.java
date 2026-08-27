package com.skaanb.DejaView.dto;

import java.io.Serializable;

// RabbitMQ üzerinden AI analiz kuyruğuna gönderilen mesaj.
// Jackson2JsonMessageConverter ile JSON'a serialize edilir.
public class TicketAnalysisMessage implements Serializable {

    private String ticketId;
    private String description;

    public TicketAnalysisMessage() {
    }

    public TicketAnalysisMessage(String ticketId, String description) {
        this.ticketId = ticketId;
        this.description = description;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
