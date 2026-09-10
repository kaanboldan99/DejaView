package com.skaanb.DejaView.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Elasticsearch'teki {@code dejaview_tickets} indeksinde duran kayıt dokümanı.
 *
 * Nasıl çalışır: uygulamanın kullanıcıya gösterdiği kayıt akışının BİRİNCİL
 * deposu burasıdır — arama, tekrar sayacı ve AI analiz sonuçları bu dokümanda
 * tutulur. Doküman kimliği rastgele değil, normalize edilmiş başlıktan
 * deterministik olarak türetilir; böylece "aynı başlık = aynı kayıt" kuralı
 * sorguya değil dokümanın kimliğine bağlanır
 * (bkz. {@link com.skaanb.DejaView.service.TicketService}).
 *
 * Alan tipleri bilinçli seçildi: {@code Keyword} alanlar (başlık, servis,
 * etiketler) birebir eşleşmeyle filtrelenir, {@code Text} alanlar (hata mesajı,
 * yığın izi, AI metinleri) tam metin aramasına girer.
 */
@Document(indexName = "dejaview_tickets")
public class TicketDocument {

    /** Doküman kimliği; normalize edilmiş başlığın SHA-256 özeti. */
    @Id
    private String id;

    /**
     * Elasticsearch'in iyimser eşzamanlılık denetimi (seq_no/primary_term) için versiyon.
     *
     * Nasıl çalışır: iki thread aynı kaydı aynı anda güncellemeye çalışırsa,
     * versiyonu eski olan {@code save()} çağrısı {@code OptimisticLockingFailureException}
     * fırlatır (bkz. {@link com.skaanb.DejaView.service.TicketMutationExecutor}) —
     * bu sayede biri diğerinin yazdığını sessizce ezmez, yani "lost update" oluşmaz.
     */
    @Version
    private Long version;

    /** Kaydın görünen başlığı; ilk oluşturmadan sonra değiştirilmez. */
    @Field(type = FieldType.Keyword)
    private String title;

    /**
     * Başlığın kırpılmış ve küçük harfe çevrilmiş hâli.
     *
     * Nasıl çalışır: aynı başlıkla açılan kayıtları büyük/küçük harf duyarsız
     * bulmak için kullanılır
     * (bkz. {@link com.skaanb.DejaView.repository.TicketRepository#findByTitleNormalized}).
     */
    @Field(type = FieldType.Keyword)
    private String titleNormalized;

    /** Hatanın metni; aramada taranan ana alan. */
    @Field(type = FieldType.Text)
    private String errorMessage;

    /** Hatanın yığın izi (stack trace). */
    @Field(type = FieldType.Text)
    private String stackTrace;

    /** Hatanın geldiği servis adı; filtre listesinde birebir eşleşmeyle kullanılır. */
    @Field(type = FieldType.Keyword)
    private String serviceName;

    /** Kaydın ilk oluşturulma zamanı; sonraki görülmelerde değişmez. */
    @Field(type = FieldType.Date)
    private Instant createdAt;

    /** Aynı hatanın en son ne zaman görüldüğü; her yeni görülmede güncellenir. */
    @Field(type = FieldType.Date)
    private Instant lastOccurrenceAt;

    /** AI'ın ürettiği ayrıntılı açıklama: "ne oldu" sorusunun cevabı. */
    @Field(type = FieldType.Text)
    private String aiGeneratedDescription;

    /**
     * AI'ın öne sürdüğü en olası kök neden: "neden oldu" sorusunun cevabı.
     *
     * Nasıl çalışır: {@link #aiGeneratedDescription} ile ayrı alanlarda tutulur
     * çünkü arayüzde ayrı bölümler olarak gösteriliyor ve biri gelmezse diğeri
     * yine de gösterilebiliyor.
     */
    @Field(type = FieldType.Text)
    private String aiRootCause;

    /** Etiketler; küçük harfe normalize edilmiş, birebir eşleşmeyle filtrelenen Keyword dizisi. */
    @Field(type = FieldType.Keyword)
    private List<String> aiTags;

    /**
     * Çözüm önerileri.
     *
     * Nasıl çalışır: aynı başlıklı hatanın birden fazla çözümü olabileceği için
     * her yeni analiz sonucu bu listeye EKLENİR, üzerine yazılmaz. Tek istisna
     * kullanıcının açık "yeniden üret" isteğidir
     * (bkz. {@link com.skaanb.DejaView.service.TicketAnalysisListener}).
     */
    @Field(type = FieldType.Text)
    private List<String> solutions = new ArrayList<>();

    /** Kaydı ilk açan kullanıcı adı; silme yetkisi kontrolünde kullanılır. */
    @Field(type = FieldType.Keyword)
    private String createdBy;

    /** Bu başlıkla kaç kez kayıt açılmaya çalışıldığı (tekrarlar dâhil). */
    @Field(type = FieldType.Integer)
    private int occurrenceCount = 1;

    /** Kaydın AI analiz sürecindeki durumu. */
    @Field(type = FieldType.Keyword)
    private TicketStatus status;

    /* --- Erişimciler: alanların anlamı yukarıdaki tanımlarda belgelendi.  --- */
    /* --- Tek istisna mergeAiTags; o kendi davranışıyla ayrıca belgelendi. --- */

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

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

    public String getAiRootCause() { return aiRootCause; }
    public void setAiRootCause(String aiRootCause) { this.aiRootCause = aiRootCause; }

    public List<String> getAiTags() { return aiTags; }
    public void setAiTags(List<String> aiTags) { this.aiTags = aiTags; }

    /**
     * Yeni etiketleri mevcutların ÜZERİNE yazmadan birleştirir.
     *
     * Nasıl çalışır: aynı kayıt hem kullanıcının elle verdiği etiketleri hem de
     * arka planda çalışan AI analizinin ürettiklerini alabilir ve ikisi de
     * korunmalıdır. {@link LinkedHashSet} tekrarları eler ama ekleme sırasını
     * bozmaz, böylece önce gelen (genelde kullanıcının kendi verdiği) etiket
     * listenin başında kalır.
     *
     * Bu mantık {@code TicketService} ve {@code TicketAnalysisListener} içinde
     * ayrı ayrı kopyalanmak yerine burada duruyor: etiketlerin nasıl
     * birleşeceğini bilmesi gereken, dokümanın kendisi.
     *
     * @param newTags eklenecek etiketler; {@code null} verilebilir, o durumda
     *                mevcut etiketler olduğu gibi kalır
     */
    public void mergeAiTags(List<String> newTags) {
        Set<String> merged = new LinkedHashSet<>();
        if (this.aiTags != null) {
            merged.addAll(this.aiTags);
        }
        if (newTags != null) {
            merged.addAll(newTags);
        }
        this.aiTags = new ArrayList<>(merged);
    }

    public List<String> getSolutions() { return solutions; }
    public void setSolutions(List<String> solutions) { this.solutions = solutions; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public int getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(int occurrenceCount) { this.occurrenceCount = occurrenceCount; }

    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
}
