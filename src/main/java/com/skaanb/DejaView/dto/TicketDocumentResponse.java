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
public class TicketDocumentResponse {

    private String id;
    private String errorMessage;
    private String stackTrace;
    private String serviceName;
    private String aiGeneratedDescription;
    private List<String> aiTags;
    private String solution;
    private Instant createdAt;
    private String createdBy;

    // TicketDocument modelinden TicketDocumentResponse DTO'suna dönüştürücü
    public static TicketDocumentResponse fromDocument(TicketDocument doc) {
        if (doc == null) {
            return null;
        }
        TicketDocumentResponse response = new TicketDocumentResponse();
        response.setId(doc.getId());
        response.setErrorMessage(doc.getErrorMessage());
        response.setStackTrace(doc.getStackTrace());
        response.setServiceName(doc.getServiceName());
        response.setAiGeneratedDescription(doc.getAiGeneratedDescription());
        response.setAiTags(doc.getAiTags());
        response.setSolution(doc.getSolution());
        response.setCreatedAt(doc.getCreatedAt());
        response.setCreatedBy(doc.getCreatedBy());
        return response;
    }

    // Service'in beklediği isim - fromDocument'a delegate ediyor
    public static TicketDocumentResponse fromTicket(TicketDocument doc) {
        return fromDocument(doc);
    }
}