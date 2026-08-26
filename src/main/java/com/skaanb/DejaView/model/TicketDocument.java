package com.skaanb.DejaView.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import java.time.Instant;
import java.util.List;

@Document(indexName = "dejaview_tickets")
public class TicketDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String errorMessage;

    @Field(type = FieldType.Text)
    private String stackTrace;

    @Field(type = FieldType.Keyword)
    private String serviceName;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Text)
    private String aiGeneratedDescription;

    @Field(type = FieldType.Keyword)
    private List<String> aiTags;

    @Field(type = FieldType.Text)
    private String solution;

    @Field(type = FieldType.Keyword)
    private String createdBy;

    // --- GETTER & SETTER METOTLARI ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getAiGeneratedDescription() { return aiGeneratedDescription; }
    public void setAiGeneratedDescription(String aiGeneratedDescription) { this.aiGeneratedDescription = aiGeneratedDescription; }

    public List<String> getAiTags() { return aiTags; }
    public void setAiTags(List<String> aiTags) { this.aiTags = aiTags; }

    public String getSolution() { return solution; }
    public void setSolution(String solution) { this.solution = solution; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}