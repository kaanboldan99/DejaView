package com.skaanb.DejaView.dto;

/**
 * Giriş isteğinin gövdesi.
 *
 * Nasıl çalışır: kimlik e-posta ile doğrulanır (kullanıcı adıyla değil);
 * {@link com.skaanb.DejaView.controller.AuthController} kullanıcıyı e-postadan
 * bulur, şifreyi BCrypt ile karşılaştırır ve başarılıysa JWT üretir. Üretilen
 * token'ın subject alanı ise kullanıcı ADIDIR.
 *
 * Sınıf bilinçli olarak dar: rol, id gibi alanlar burada yok, çünkü giriş
 * isteğinin bunları belirlemeye hiçbir zaman yetkisi olmamalı.
 */
public class LoginRequest {

    /** Giriş yapılacak hesabın e-posta adresi. */
    private String email;

    /** Düz metin şifre; sunucuda hash'lenmiş değerle karşılaştırılır, asla saklanmaz. */
    private String password;

    /**
     * @return giriş için verilen e-posta
     */
    public String getEmail() {
        return email;
    }

    /**
     * @param email giriş için kullanılacak e-posta
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * @return düz metin şifre
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password düz metin şifre
     */
    public void setPassword(String password) {
        this.password = password;
    }
}
