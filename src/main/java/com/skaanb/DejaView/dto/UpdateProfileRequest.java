package com.skaanb.DejaView.dto;

/**
 * Kullanıcının kendi profilinde değiştirebildiği alanlar.
 *
 * username değiştirilemez: JWT'nin subject'i olduğu için değişmesi mevcut
 * oturumu geçersiz kılar. Rol de burada yok — kullanıcı kendi rolünü
 * yükseltememeli.
 */
public class UpdateProfileRequest {

    private String email;
    private String phoneNumber;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}
