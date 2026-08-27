package com.skaanb.DejaView.dto;

import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.model.TicketStatus;
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
    private String title;
    private String errorMessage;
    private String stackTrace;
    private String serviceName;
    private String aiGeneratedDescription;
    private List<String> aiTags;
    private List<String> solutions;
    private Instant createdAt; // Model sınıfınızdaki Instant tipiyle eşitlendi
    private Instant lastOccurrenceAt;
    private String createdBy;
    private int occurrenceCount;
    private TicketStatus status;

    // Elasticsearch Document nesnesinden DTO üreten static metot
    public static TicketResponse fromTicket(TicketDocument ticketDocument) {
        if (ticketDocument == null) {
            return null;
        }
        TicketResponse response = new TicketResponse();
        response.setId(ticketDocument.getId());
        response.setTitle(ticketDocument.getTitle());
        response.setErrorMessage(ticketDocument.getErrorMessage());
        response.setStackTrace(ticketDocument.getStackTrace());
        response.setServiceName(ticketDocument.getServiceName());
        response.setAiGeneratedDescription(ticketDocument.getAiGeneratedDescription());
        response.setAiTags(ticketDocument.getAiTags());
        response.setSolutions(ticketDocument.getSolutions());
        response.setCreatedAt(ticketDocument.getCreatedAt());
        response.setLastOccurrenceAt(ticketDocument.getLastOccurrenceAt());
        response.setCreatedBy(ticketDocument.getCreatedBy());
        response.setOccurrenceCount(ticketDocument.getOccurrenceCount());
        response.setStatus(ticketDocument.getStatus());
        return response;
    }
}
