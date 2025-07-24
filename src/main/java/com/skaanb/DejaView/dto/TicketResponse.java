package com.skaanb.DejaView.dto;

import java.time.LocalDateTime;
import java.util.List;

public class TicketResponse {

    private Long id;
    private String title;
    private String description;
    private String summary;
    private List<String> tags;
    private LocalDateTime createdAt;
    private Long userId;
    private String username;

    public TicketResponse() {}

    public TicketResponse(Long id, String title, String description, String summary,
                          List<String> tags, LocalDateTime createdAt, Long userId, String username) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.summary = summary;
        this.tags = tags;
        this.createdAt = createdAt;
        this.userId = userId;
        this.username = username;
    }

    // Getters & Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
