package com.skaanb.DejaView.dto;

import com.skaanb.DejaView.model.TicketDocument;

public class TicketDocumentResponse {

    public static TicketResponse fromDocument(TicketDocument doc) {
        TicketResponse response = new TicketResponse();
        response.setElasticId(doc.getId());        // Elasticsearch ID (String)
        response.setTitle(doc.getTitle());
        response.setSummary(doc.getSummary());
        response.setDescription(null);             // Description indexlenmediği için
        response.setTags(null);
        response.setCreatedAt(doc.getCreatedAt());
        return response;
    }

}
