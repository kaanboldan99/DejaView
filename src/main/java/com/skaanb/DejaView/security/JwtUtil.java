package com.skaanb.DejaView.security;

import com.skaanb.DejaView.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * JWT üretimi ve doğrulaması.
 *
 * Nasıl çalışır: token HS256 ile, tek bir simetrik anahtarla imzalanır;
 * subject alanı kullanıcı ADIDIR. Sunucu oturum tutmadığı için kimlik her
 * istekte token'dan yeniden kurulur
 * (bkz. {@link JwtAuthenticationFilter}).
 *
 * İmzalama anahtarı ARTIK KODDA SABİT DEĞİL. Önceden burada sabit bir metin
 * duruyordu ve açık bir depoya girmişti; anahtarı gören herkes istediği
 * kullanıcı adına — admin dâhil — geçerli token üretebiliyordu. Anahtar artık
 * {@code jwt.secret} (üretimde {@code JWT_SECRET} ortam değişkeni) üzerinden
 * geliyor ve eksik/kısa olduğunda uygulama AÇILMIYOR: sessizce zayıf bir
 * varsayılana düşmek, bu sınıfın düzeltmeye çalıştığı hatanın ta kendisi.
 *
 * Doğrulama metotları hata FIRLATMAZ; geçersiz token'da uyarı loglayıp
 * olumsuz sonuç dönerler, çünkü bozuk bir token sunucu hatası değil olağan
 * bir istemci durumudur.
 */
@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    /** HS256 en az 256 bit (32 byte) anahtar gerektirir. */
    private static final int MIN_SECRET_BYTES = 32;

    /** İmzalama anahtarı; kurucuda bir kez üretilir ve değişmez. */
    private final Key key;

    /** Token'ın geçerlilik süresi (milisaniye). */
    private final long expirationMs;

    /**
     * Anahtarı doğrulayıp imzalama anahtarını hazırlar.
     *
     * Nasıl çalışır: anahtar boşsa ya da 32 byte'tan kısaysa
     * {@link IllegalStateException} fırlatılır ve uygulama hiç açılmaz. Bu
     * bilinçli: zayıf bir varsayılana sessizce düşmek, sorunun fark edilmeden
     * üretime çıkmasına yol açardı. Hata mesajları düzeltmenin nasıl
     * yapılacağını da söyler.
     *
     * @param secret       {@code jwt.secret} ayarı; en az 32 byte rastgele değer
     * @param expirationMs {@code jwt.expiration-ms} ayarı; varsayılan 24 saat
     * @throws IllegalStateException anahtar tanımsız veya çok kısaysa
     */
    public JwtUtil(@Value("${jwt.secret:}") String secret,
                   @Value("${jwt.expiration-ms:86400000}") long expirationMs) {

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT imzalama anahtarı tanımlı değil. JWT_SECRET ortam değişkenini "
                    + "(veya jwt.secret ayarını) en az " + MIN_SECRET_BYTES + " byte'lık rastgele "
                    + "bir değerle set edin. Örnek: openssl rand -base64 48");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT imzalama anahtarı çok kısa (" + secretBytes.length + " byte). "
                    + "JWT_SECRET en az " + MIN_SECRET_BYTES + " byte olmalı. "
                    + "Örnek: openssl rand -base64 48");
        }

        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * Kullanıcı için imzalı bir token üretir.
     *
     * Nasıl çalışır: subject alanına kullanıcı ADI yazılır (kimlik numarası
     * değil), üretim ve son kullanma zamanları eklenir, HS256 ile imzalanır.
     * Token'a rol gibi ek bilgi KONULMAZ; yetkiler her istekte veritabanından
     * tazelenir, böylece rol değişikliği anında etkili olur.
     *
     * @param user token üretilecek kullanıcı
     * @return imzalanmış, kodlanmış JWT metni
     */
    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Token'dan kullanıcı adını çıkarır.
     *
     * Nasıl çalışır: token bozuk, süresi geçmiş ya da imzası geçersizse hata
     * fırlatılmaz; uyarı loglanıp {@code null} dönülür. Çağıran taraf
     * ({@link JwtAuthenticationFilter}) bu durumda kullanıcıyı doğrulanmamış
     * kabul edip isteği zincire bırakır.
     *
     * @param token ham JWT metni
     * @return token'daki kullanıcı adı; token okunamıyorsa {@code null}
     */
    public String extractUsername(String token) {
        try {
            return getClaims(token).getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("JWT'den kullanıcı adı okunamadı: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Token'ın imza ve süre açısından geçerli olup olmadığını söyler.
     *
     * Nasıl çalışır: talepleri çözmeyi dener; çözme başarılıysa imza doğrudur
     * ve süresi geçmemiştir (kütüphane süresi geçmiş token'da hata fırlatır).
     * Bu sürüm token'ı bir kullanıcıyla EŞLEŞTİRMEZ.
     *
     * @param token ham JWT metni
     * @return token geçerliyse {@code true}
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("Geçersiz JWT: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Token'ı belirli bir kullanıcıyla eşleştirerek doğrular.
     *
     * Nasıl çalışır: imza geçerliliğine ek olarak iki koşul aranır — token'ın
     * subject alanı verilen kullanıcının adıyla aynı olmalı ve son kullanma
     * zamanı geçmemiş olmalı. Kullanıcı karşılaştırması, başka bir hesap için
     * üretilmiş geçerli imzalı bir token'ın kabul edilmesini engeller.
     *
     * @param token       ham JWT metni
     * @param userDetails token'ın ait olması beklenen kullanıcı
     * @return token geçerli ve bu kullanıcıya aitse {@code true}
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            Claims claims = getClaims(token);
            String username = claims.getSubject();
            Date expiration = claims.getExpiration();

            return (username.equals(userDetails.getUsername()) && !expiration.before(new Date()));
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("Geçersiz JWT: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Token'ı imzasıyla birlikte çözüp içindeki talepleri döner.
     *
     * Nasıl çalışır: ayrıştırıcıya imzalama anahtarı verildiği için imza
     * doğrulaması çözme sırasında yapılır; imza tutmazsa ya da süre geçmişse
     * hata fırlatılır. Bu sınıftaki doğrulama metotlarının hepsi bu tek noktadan
     * geçer.
     *
     * @param token ham JWT metni
     * @return token'ın içindeki talepler
     * @throws JwtException             imza geçersiz, biçim bozuk veya süre geçmişse
     * @throws IllegalArgumentException token boş veya {@code null} ise
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .setSigningKey(key)
                .parseClaimsJws(token)
                .getBody();
    }
}
