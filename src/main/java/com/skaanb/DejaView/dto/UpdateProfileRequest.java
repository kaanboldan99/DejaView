package com.skaanb.DejaView.dto;

/**
 * Kullanıcının kendi profilinde değiştirebildiği alanlar.
 *
 * Nasıl çalışır: yalnızca gönderilen ({@code null} olmayan) alanlar güncellenir,
 * böylece istemci tek bir alanı değiştirmek için tüm profili göndermek zorunda
 * kalmaz (bkz. {@link com.skaanb.DejaView.service.UserService#updateOwnProfile}).
 *
 * {@code username} bilinçli olarak yok: JWT'nin subject alanı olduğu için
 * değişmesi mevcut oturumu geçersiz kılar. {@code role} de yok — kullanıcı
 * kendi yetkisini yükseltememeli.
 */
public class UpdateProfileRequest {

    /** Yeni e-posta; gönderilmezse ({@code null}) mevcut e-posta korunur. */
    private String email;

    /**
     * Yeni telefon numarası.
     *
     * Nasıl çalışır: gönderilmezse ({@code null}) mevcut numara korunur; BOŞ
     * string gönderilmesi ise "telefonu kaldır" anlamına gelir.
     */
    private String phoneNumber;

    /**
     * @return yeni e-posta; değiştirilmek istenmiyorsa {@code null}
     */
    public String getEmail() { return email; }

    /**
     * @param email yeni e-posta
     */
    public void setEmail(String email) { this.email = email; }

    /**
     * @return yeni telefon numarası; değiştirilmek istenmiyorsa {@code null}
     */
    public String getPhoneNumber() { return phoneNumber; }

    /**
     * @param phoneNumber yeni telefon numarası; boş string numarayı kaldırır
     */
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}
