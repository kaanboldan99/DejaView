package com.skaanb.DejaView.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * İlişkisel veritabanındaki (H2/JPA) kayıt varlığı.
 *
 * Nasıl çalışır: {@code tickets} tablosuna eşlenir; kimlik veritabanı tarafından
 * {@code IDENTITY} stratejisiyle üretilir, etiketler ayrı bir
 * {@code ticket_tags} yan tablosunda tutulur ({@code @ElementCollection}) ve
 * her kayıt bir {@link User}'a bağlıdır.
 *
 * Kullanıcıya dönen kayıt akışının BİRİNCİL deposu bu sınıf değil,
 * Elasticsearch'teki {@link TicketDocument}'tır: arama, tekrar sayacı ve AI
 * analizi orada yürür. Bu varlık JPA tarafındaki ilişkisel şemayı temsil eder
 * ({@link User#tickets} üzerinden erişilir).
 *
 * Kullanıcı ilişkisi {@code LAZY}: kullanıcı bilgisi çoğu sorguda gerekmiyor,
 * her kayıt okumasında bir de kullanıcı satırı çekmek gereksiz maliyet olurdu.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    /** Birincil anahtar; veritabanı tarafından üretilir. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kaydın başlığı; zorunlu alan. */
    @Column(nullable = false)
    private String title;

    /** Hatanın serbest metin açıklaması; AI analizine girdi olan alan. */
    @Column(length = 5000)
    private String description;

    /** AI tarafından üretilen özet. Analiz tamamlanana kadar boş kalır. */
    @Column(length = 2000)
    private String summary;

    /**
     * Kaydın etiketleri.
     *
     * Nasıl çalışır: temel tip koleksiyonu olduğu için ayrı bir varlık değil,
     * {@code ticket_tags} yan tablosunda {@code ticket_id} yabancı anahtarıyla
     * saklanır.
     */
    @ElementCollection
    @CollectionTable(name = "ticket_tags", joinColumns = @JoinColumn(name = "ticket_id"))
    @Column(name = "tag")
    private List<String> tags;

    /** Oluşturulma zamanı; nesne kurulduğu anda atanır. */
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Kaydı açan kullanıcı; zorunlu ve gecikmeli (LAZY) yüklenir. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * JPA'nın zorunlu kıldığı parametresiz kurucu.
     *
     * Nasıl çalışır: Hibernate nesneyi veritabanından okurken önce bu kurucuyla
     * boş bir örnek oluşturur, sonra alanları doldurur. Uygulama kodunda
     * doğrudan kullanılması amaçlanmaz.
     */
    public Ticket() {}

    /**
     * Yeni bir kayıt oluşturur ve oluşturulma zamanını o ana ayarlar.
     *
     * @param title       kaydın başlığı
     * @param description hatanın serbest metin açıklaması
     * @param summary     AI özeti; oluşturma anında genelde {@code null} verilir
     * @param tags        etiket listesi; {@code null} olabilir
     * @param user        kaydı açan kullanıcı
     */
    public Ticket(String title, String description, String summary, List<String> tags, User user) {
        this.title = title;
        this.description = description;
        this.summary = summary;
        this.tags = tags;
        this.user = user;
        this.createdAt = LocalDateTime.now();
    }

    /* --- Erişimciler: alanların anlamı yukarıdaki tanımlarda belgelendi, --- */
    /* --- bu metotların okuma/yazma dışında bir davranışı yoktur.         --- */

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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
