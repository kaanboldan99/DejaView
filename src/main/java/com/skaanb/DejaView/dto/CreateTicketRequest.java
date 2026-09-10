package com.skaanb.DejaView.dto;

import java.util.List;

/**
 * Elle kayıt açma isteğinin gövdesi.
 *
 * Nasıl çalışır: {@link com.skaanb.DejaView.service.TicketService#createTicket}
 * bu isteği alır, başlığı normalize eder ve aynı başlıkla açılmış bir kayıt
 * varsa yeni kayıt yaratmak yerine mevcut kaydın tekrar sayacını artırır.
 * Yani bu DTO "yeni kayıt" değil, "şu hata görüldü" bildirimidir.
 *
 * Alanların hiçbiri zorunlu değil: eksik gelen her alan için servis makul bir
 * varsayılan atar, böylece eski istemciler bozulmaz.
 */
public class CreateTicketRequest {

    /** Kaydın başlığı; boş bırakılırsa servis "Başlıksız Kayıt" atar. */
    private String title;

    /** Hatanın açıklaması; AI analizine girdi olan metin. */
    private String description;

    /** Kullanıcının elle verdiği etiketler; AI etiketleriyle birleştirilir, ezilmez. */
    private List<String> tags;

    /** Kaydı açan kullanıcının kimliği; kimlik esas olarak JWT'den okunduğu için isteğe bağlıdır. */
    private Long userId;

    /**
     * Kaydın hangi servise ait olduğu.
     *
     * Nasıl çalışır: boş bırakılırsa {@code TicketService} varsayılan bir değer
     * atar; eski istemcilerin bozulmaması için zorunlu değil.
     */
    private String serviceName;

    /**
     * Jackson'ın gövdeyi bağlaması için gereken parametresiz kurucu.
     */
    public CreateTicketRequest() {}

    /**
     * Servis adı verilmeden kayıt isteği oluşturur.
     *
     * Nasıl çalışır: {@code serviceName} boş kalır ve servis tarafında
     * varsayılana çevrilir.
     *
     * @param title       kaydın başlığı
     * @param description hatanın açıklaması
     * @param tags        kullanıcının verdiği etiketler; {@code null} olabilir
     * @param userId      kaydı açan kullanıcının kimliği
     */
    public CreateTicketRequest(String title, String description, List<String> tags, Long userId) {
        this.title = title;
        this.description = description;
        this.tags = tags;
        this.userId = userId;
    }

    /**
     * Servis adıyla birlikte kayıt isteği oluşturur.
     *
     * @param title       kaydın başlığı
     * @param description hatanın açıklaması
     * @param tags        kullanıcının verdiği etiketler; {@code null} olabilir
     * @param userId      kaydı açan kullanıcının kimliği
     * @param serviceName hatanın geldiği servis adı
     */
    public CreateTicketRequest(String title, String description, List<String> tags, Long userId, String serviceName) {
        this(title, description, tags, userId);
        this.serviceName = serviceName;
    }

    /* --- Erişimciler: alanların anlamı yukarıdaki tanımlarda belgelendi, --- */
    /* --- bu metotların okuma/yazma dışında bir davranışı yoktur.         --- */

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
