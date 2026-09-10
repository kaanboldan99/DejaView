package com.skaanb.DejaView.dto;

import com.skaanb.DejaView.model.Role;
import com.skaanb.DejaView.model.User;

import java.time.LocalDateTime;

/**
 * Kullanıcının kendi profil bilgisinin dışarı dönen hâli.
 *
 * Nasıl çalışır: {@link User} varlığından {@link #from(User)} ile üretilir ve
 * yalnızca gösterilmesi güvenli alanları taşır. Şifre bu DTO'da bilinçli olarak
 * YOK: varlığı doğrudan döndürmek BCrypt hash'ini istemciye ve erişim
 * loglarına sızdırıyordu.
 *
 * Hem profil ucu hem de kayıt ucu bu DTO ile yanıt verir; ikisinin de aynı
 * gövdeyi üretmesi istemci tarafında tek bir modelle çalışmayı sağlıyor.
 */
public class UserProfileResponse {

    /** Kullanıcının veritabanı kimliği. */
    private Long id;

    /** Kullanıcı adı; JWT'nin subject alanı. */
    private String username;

    /** E-posta adresi. */
    private String email;

    /** Telefon numarası; tanımlı değilse {@code null}. */
    private String phoneNumber;

    /** Yetki seviyesi. */
    private Role role;

    /** Hesabın oluşturulma zamanı. */
    private LocalDateTime createdAt;

    /**
     * Varlıktan yanıt DTO'su üretir.
     *
     * Nasıl çalışır: alanlar tek tek kopyalanır; kopyalanmayan her alan
     * (özellikle şifre hash'i) tanım gereği dışarı çıkmaz. Yeni bir alan
     * eklendiğinde buraya da eklenmediği sürece yanıtta görünmez — bu
     * "unutulduğunda sızdırmayan" varsayılan bilinçli.
     *
     * @param user kaynak kullanıcı varlığı; {@code null} olmamalı
     * @return yalnızca gösterilmesi güvenli alanları taşıyan DTO
     */
    public static UserProfileResponse from(User user) {
        UserProfileResponse dto = new UserProfileResponse();
        dto.id = user.getId();
        dto.username = user.getUsername();
        dto.email = user.getEmail();
        dto.phoneNumber = user.getPhoneNumber();
        dto.role = user.getRole();
        dto.createdAt = user.getCreatedAt();
        return dto;
    }

    /* --- Erişimciler: alanların anlamı yukarıdaki tanımlarda belgelendi, --- */
    /* --- bu metotların okuma/yazma dışında bir davranışı yoktur.         --- */

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
