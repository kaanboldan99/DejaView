package com.skaanb.DejaView.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(indexName = "dejaview_tickets")
public class TicketDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String title;

    // title'ın trim+lowercase edilmiş hali; aynı başlıkla açılan kayıtları
    // büyük/küçük harf duyarsız şekilde bulmak için (bkz. TicketRepository.findByTitleNormalized)
    @Field(type = FieldType.Keyword)
    private String titleNormalized;

    @Field(type = FieldType.Text)
    private String errorMessage;

    @Field(type = FieldType.Text)
    private String stackTrace;

    @Field(type = FieldType.Keyword)
    private String serviceName;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Date)
    private Instant lastOccurrenceAt;

    @Field(type = FieldType.Text)
    private String aiGeneratedDescription;

    @Field(type = FieldType.Keyword)
    private List<String> aiTags;

    // Aynı başlıklı hatanın birden fazla çözümü olabilir; her yeni AI analizi
    // sonucu buraya eklenir (üzerine yazılmaz).
    @Field(type = FieldType.Text)
    private List<String> solutions = new ArrayList<>();

    @Field(type = FieldType.Keyword)
    private String createdBy;

    // Bu başlıkla kaç kez ticket açılmaya çalışıldığı (duplicate'ler dahil)
    @Field(type = FieldType.Integer)
    private int occurrenceCount = 1;

    @Field(type = FieldType.Keyword)
    private TicketStatus status;

    // --- GETTER & SETTER METOTLARI ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTitleNormalized() { return titleNormalized; }
    public void setTitleNormalized(String titleNormalized) { this.titleNormalized = titleNormalized; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastOccurrenceAt() { return lastOccurrenceAt; }
    public void setLastOccurrenceAt(Instant lastOccurrenceAt) { this.lastOccurrenceAt = lastOccurrenceAt; }

    public String getAiGeneratedDescription() { return aiGeneratedDescription; }
    public void setAiGeneratedDescription(String aiGeneratedDescription) { this.aiGeneratedDescription = aiGeneratedDescription; }

    public List<String> getAiTags() { return aiTags; }
    public void setAiTags(List<String> aiTags) { this.aiTags = aiTags; }

    public List<String> getSolutions() { return solutions; }
    public void setSolutions(List<String> solutions) { this.solutions = solutions; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public int getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(int occurrenceCount) { this.occurrenceCount = occurrenceCount; }

    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
}
