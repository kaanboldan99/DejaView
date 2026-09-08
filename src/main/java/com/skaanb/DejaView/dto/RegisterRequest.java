package com.skaanb.DejaView.dto;

/**
 * Yeni kullanıcı kaydında istemciden kabul edilen alanlar.
 *
 * Bilinçli olarak DAR tutuldu: {@code id} ve {@code role} burada YOK. Önceden
 * register ucu ham {@code User} entity'sini bağlıyordu; istemci gövdeye
 * {@code "role":"ADMIN"} koyarak kendini admin yapabiliyor, {@code "id"} koyarak
 * da var olan bir kullanıcının satırını ezebiliyordu (save() id ile çağrıldığında
 * INSERT değil merge yapar).
 *
 * Rol her zaman sunucuda atanır. Bir kullanıcıyı ADMIN yapmak gerekiyorsa bu,
 * açıkça ADMIN yetkisi isteyen ayrı bir uçtan yapılmalı — register'ın içine
 * gizlenmemeli.
 */
public class RegisterRequest {

    private String username;
    private String password;
    private String email;
    private String phoneNumber;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}
