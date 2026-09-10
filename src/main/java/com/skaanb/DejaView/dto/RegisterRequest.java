package com.skaanb.DejaView.dto;

/**
 * Yeni kullanıcı kaydında istemciden kabul edilen alanlar.
 *
 * Nasıl çalışır: bilinçli olarak DAR tutuldu — {@code id} ve {@code role}
 * burada YOK. Önceden kayıt ucu ham {@code User} varlığını bağlıyordu; istemci
 * gövdeye {@code "role":"ADMIN"} koyarak kendini admin yapabiliyor, {@code "id"}
 * koyarak da var olan bir kullanıcının satırını ezebiliyordu ({@code save()}
 * kimlikle çağrıldığında INSERT değil merge yapar).
 *
 * Rol her zaman sunucuda atanır (bkz.
 * {@link com.skaanb.DejaView.service.UserService#createUser}). Bir kullanıcıyı
 * ADMIN yapmak gerekiyorsa bu, açıkça ADMIN yetkisi isteyen ayrı bir uçtan
 * yapılmalı — kaydın içine gizlenmemeli.
 */
public class RegisterRequest {

    /** İstenen kullanıcı adı; benzersiz olmalı, sonradan değiştirilemez. */
    private String username;

    /** Düz metin şifre; sunucuda BCrypt ile hash'lenerek saklanır. */
    private String password;

    /** E-posta; benzersiz olmalı ve giriş bu alanla yapılır. */
    private String email;

    /** Telefon numarası; isteğe bağlı, benzersiz. */
    private String phoneNumber;

    /**
     * @return istenen kullanıcı adı
     */
    public String getUsername() { return username; }

    /**
     * @param username istenen kullanıcı adı
     */
    public void setUsername(String username) { this.username = username; }

    /**
     * @return düz metin şifre
     */
    public String getPassword() { return password; }

    /**
     * @param password düz metin şifre; hash'leme çağıran serviste yapılır
     */
    public void setPassword(String password) { this.password = password; }

    /**
     * @return e-posta adresi
     */
    public String getEmail() { return email; }

    /**
     * @param email e-posta adresi
     */
    public void setEmail(String email) { this.email = email; }

    /**
     * @return telefon numarası; verilmediyse {@code null}
     */
    public String getPhoneNumber() { return phoneNumber; }

    /**
     * @param phoneNumber telefon numarası; isteğe bağlı
     */
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}
