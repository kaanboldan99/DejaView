package com.skaanb.DejaView.dto;

import java.util.List;

public class CreateTicketRequest {

    private String title;
    private String description;
    private List<String> tags;
    private Long userId;

    /**
     * Kaydın hangi servise ait olduğu. Boş bırakılırsa TicketService
     * varsayılan bir değer atar; eski istemcilerin bozulmaması için zorunlu değil.
     */
    private String serviceName;

    public CreateTicketRequest() {}

    public CreateTicketRequest(String title, String description, List<String> tags, Long userId) {
        this.title = title;
        this.description = description;
        this.tags = tags;
        this.userId = userId;
    }

    public CreateTicketRequest(String title, String description, List<String> tags, Long userId, String serviceName) {
        this(title, description, tags, userId);
        this.serviceName = serviceName;
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

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
