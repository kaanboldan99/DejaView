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
 * İmzalama anahtarı ARTIK KODDA SABİT DEĞİL. Önceden burada sabit bir string
 * duruyordu ve public bir depoya girmişti; anahtarı gören herkes istediği
 * kullanıcı adına — admin dahil — geçerli token üretebiliyordu. Anahtar artık
 * {@code jwt.secret} (üretimde {@code JWT_SECRET} ortam değişkeni) üzerinden
 * geliyor ve eksik/kısa olduğunda uygulama AÇILMIYOR: sessizce zayıf bir
 * varsayılana düşmek, bu sınıfın düzeltmeye çalıştığı hatanın ta kendisi.
 */
@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    /** HS256 en az 256 bit (32 byte) anahtar gerektirir. */
    private static final int MIN_SECRET_BYTES = 32;

    private final Key key;
    private final long expirationMs;

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

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Token bozuk/geçersiz olabilir; çağıran taraf (JwtAuthenticationFilter)
    // bu durumu try/catch ile ele alıp kullanıcıyı doğrulanmamış olarak
    // isteğe devam ettirir.
    public String extractUsername(String token) {
        try {
            return getClaims(token).getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("JWT'den kullanıcı adı okunamadı: {}", e.getMessage());
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("Geçersiz JWT: {}", e.getMessage());
            return false;
        }
    }

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


    private Claims getClaims(String token) {
        return Jwts.parser()
                .setSigningKey(key)
                .parseClaimsJws(token)
                .getBody();
    }
}
