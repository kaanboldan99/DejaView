package com.skaanb.DejaView.security;

import com.skaanb.DejaView.model.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    private static final long EXPIRATION_TIME = 1000 * 60 * 60 * 24; // 24 saat
    private static final Key key = Keys.hmacShaKeyFor("mySuperSecretKeyForJwtGeneration123456789012345".getBytes()); // en az 256-bit (32 byte)

    public static String generateToken(User user) {
        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key,SignatureAlgorithm.HS256)
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
