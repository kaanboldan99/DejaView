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
public class TicketDocumentResponse {

    private String id;
    private String title;
    private String errorMessage;
    private String stackTrace;
    private String serviceName;
    private String aiGeneratedDescription;
    private List<String> aiTags;
    private List<String> solutions;
    private Instant createdAt;
    private String createdBy;
    private int occurrenceCount;
    private TicketStatus status;

    // TicketDocument modelinden TicketDocumentResponse DTO'suna dönüştürücü
    public static TicketDocumentResponse fromDocument(TicketDocument doc) {
        if (doc == null) {
            return null;
        }
        TicketDocumentResponse response = new TicketDocumentResponse();
        response.setId(doc.getId());
        response.setTitle(doc.getTitle());
        response.setErrorMessage(doc.getErrorMessage());
        response.setStackTrace(doc.getStackTrace());
        response.setServiceName(doc.getServiceName());
        response.setAiGeneratedDescription(doc.getAiGeneratedDescription());
        response.setAiTags(doc.getAiTags());
        response.setSolutions(doc.getSolutions());
        response.setCreatedAt(doc.getCreatedAt());
        response.setCreatedBy(doc.getCreatedBy());
        response.setOccurrenceCount(doc.getOccurrenceCount());
        response.setStatus(doc.getStatus());
        return response;
    }

    // Service'in beklediği isim - fromDocument'a delegate ediyor
    public static TicketDocumentResponse fromTicket(TicketDocument doc) {
        return fromDocument(doc);
    }
}
