package com.skaanb.DejaView.dto;

import com.skaanb.DejaView.model.TicketDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {

    private String id; // Elasticsearch döküman ID'si String tipindedir
    private String errorMessage;
    private String stackTrace;
    private String serviceName;
    private String aiGeneratedDescription;
    private List<String> aiTags;
    private String solution;
    private Instant createdAt; // Model sınıfınızdaki Instant tipiyle eşitlendi
    private String createdBy;

    // Elasticsearch Document nesnesinden DTO üreten static metot
    public static TicketResponse fromTicket(TicketDocument ticketDocument) {
        if (ticketDocument == null) {
            return null;
        }
        TicketResponse response = new TicketResponse();
        response.setId(ticketDocument.getId());
        response.setErrorMessage(ticketDocument.getErrorMessage());
        response.setStackTrace(ticketDocument.getStackTrace());
        response.setServiceName(ticketDocument.getServiceName());
        response.setAiGeneratedDescription(ticketDocument.getAiGeneratedDescription());
        response.setAiTags(ticketDocument.getAiTags());
        response.setSolution(ticketDocument.getSolution());
        response.setCreatedAt(ticketDocument.getCreatedAt());
        response.setCreatedBy(ticketDocument.getCreatedBy());
        return response;
    }
}