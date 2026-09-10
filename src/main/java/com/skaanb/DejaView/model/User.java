package com.skaanb.DejaView.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Kullanıcı varlığı; kimlik doğrulama ve yetkilendirmenin dayandığı kayıt.
 *
 * Nasıl çalışır: {@code users} tablosuna eşlenir. Kullanıcı adı, e-posta ve
 * telefon benzersizdir; şifre asla düz metin tutulmaz, BCrypt ile hash'lenerek
 * yazılır (bkz. {@link com.skaanb.DejaView.service.UserService#createUser}).
 * Giriş sırasında kullanıcı e-posta ile bulunur, JWT'nin subject alanı ise
 * kullanıcı adıdır — bu yüzden kullanıcı adı değiştirilemez bir alandır.
 *
 * Bu varlık istemciye DOĞRUDAN döndürülmez; yanıtlar
 * {@link com.skaanb.DejaView.dto.UserProfileResponse} üzerinden verilir.
 */
@Entity
@Table(name = "users")
public class User {

    /** Birincil anahtar; veritabanı tarafından üretilir, istemciden asla alınmaz. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kullanıcı adı; benzersiz ve JWT'nin subject alanı olduğu için değiştirilemez. */
    @Column(nullable = false, unique = true)
    private String username;

    /**
     * BCrypt ile hash'lenmiş şifre.
     *
     * Nasıl çalışır: {@code @JsonIgnore} sayesinde varlık bir yanıtta yanlışlıkla
     * serialize edilse bile hash dışarı sızmaz. Asıl koruma yanıt DTO'larıdır
     * ({@link com.skaanb.DejaView.dto.UserProfileResponse}); bu ikinci savunma hattı.
     */
    @JsonIgnore
    @Column(nullable = false)
    private String password;

    /** E-posta; benzersiz ve giriş akışının kullandığı alan. */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Telefon numarası; benzersiz, isteğe bağlı.
     *
     * Nasıl çalışır: telefon üzerinden kayıt açma akışı (kullanıcı adı + telefon
     * eşleşmesi) için tutuluyor. Telefon/IVR entegrasyonunun kendisi henüz
     * kurulmadı; bu yalnızca altyapı hazırlığı.
     *
     * Benzersiz sütunda birden fazla {@code NULL}'a izin verilir ama boş
     * string'e verilmez; bu yüzden "telefonu kaldır" işlemi boş string değil
     * {@code null} yazar (bkz. {@link com.skaanb.DejaView.service.UserService}).
     */
    @Column(unique = true)
    private String phoneNumber;

    /**
     * Yetki seviyesi; varsayılan {@link Role#USER}.
     *
     * Nasıl çalışır: {@code EnumType.STRING} ile adıyla saklanır (sıra numarasıyla
     * değil), böylece enum'a yeni bir değer eklendiğinde mevcut satırların anlamı
     * kaymaz. Rol istemciden asla alınmaz, sunucu tarafından atanır.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    /** Hesabın oluşturulma zamanı; nesne kurulduğu anda atanır. */
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Kullanıcının açtığı kayıtlar.
     *
     * Nasıl çalışır: {@code cascade = ALL} ve {@code orphanRemoval = true}
     * olduğu için kullanıcı silindiğinde kayıtları da silinir; listeden çıkarılan
     * bir kayıt da yetim kalmaz, veritabanından düşer.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Ticket> tickets;

    /**
     * JPA'nın zorunlu kıldığı parametresiz kurucu.
     *
     * Nasıl çalışır: Hibernate nesneyi veritabanından okurken önce bu kurucuyla
     * boş bir örnek oluşturur, sonra alanları doldurur.
     */
    public User() {}

    /**
     * Yeni bir kullanıcı oluşturur ve oluşturulma zamanını o ana ayarlar.
     *
     * @param username kullanıcı adı; benzersiz olmalı
     * @param password ZATEN HASH'LENMİŞ şifre — bu kurucu hash'leme yapmaz,
     *                 hash'leme çağıran serviste yapılır
     * @param email    e-posta; benzersiz olmalı
     */
    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Ticket> getTickets() {
        return tickets;
    }

    public void setTickets(List<Ticket> tickets) {
        this.tickets = tickets;
    }
}
