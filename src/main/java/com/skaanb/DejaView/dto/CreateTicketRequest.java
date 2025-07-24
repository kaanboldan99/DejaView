package com.skaanb.DejaView.dto;

import java.util.List;

public class CreateTicketRequest {

    private String title;
    private String description;
    private List<String> tags;
    private Long userId;

    public CreateTicketRequest() {}

    public CreateTicketRequest(String title, String description, List<String> tags, Long userId) {
        this.title = title;
        this.description = description;
        this.tags = tags;
        this.userId = userId;
    }

    // Getters & Setters

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

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
