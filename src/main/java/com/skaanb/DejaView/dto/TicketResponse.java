package com.skaanb.DejaView.dto;

import com.skaanb.DejaView.model.Ticket;

import java.time.LocalDateTime;
import java.util.List;

public class TicketResponse {


    private Long ticketId;       // Veritabanındaki ID
    private String elasticId;    // Elasticsearch’teki ID
    private String title;
    private String description;
    private String summary;
    private List<String> tags;
    private LocalDateTime createdAt;
    private Long userId;
    private String username;

    public TicketResponse() {}

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getElasticId() {
        return elasticId;
    }

    public void setElasticId(String elasticId) {
        this.elasticId = elasticId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public static TicketResponse fromTicket(Ticket ticket) {
        TicketResponse response = new TicketResponse();
        response.setTicketId(ticket.getId());                         // Veritabanı ID
        response.setTitle(ticket.getTitle());
        response.setSummary(ticket.getSummary());
        response.setDescription(ticket.getDescription());
        response.setTags(ticket.getTags());                           // Tags varsa eklemeyi unutma
        response.setCreatedAt(ticket.getCreatedAt());
        response.setUserId(ticket.getUser().getId());
        response.setUsername(ticket.getUser().getUsername());
        return response;
    }

}
