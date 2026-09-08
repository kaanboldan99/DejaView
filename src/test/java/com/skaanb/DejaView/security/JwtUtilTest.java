package com.skaanb.DejaView.security;

import com.skaanb.DejaView.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * İmzalama anahtarının konfigürasyondan geldiğini ve eksik/zayıf anahtarla
 * uygulamanın sessizce çalışmaya devam etmediğini doğrular.
 *
 * Arka plan: anahtar önceden JwtUtil içinde sabit kodluydu ve public bir depoya
 * girmişti — yani onu gören herkes istediği kullanıcı adına geçerli token
 * üretebiliyordu. Aşağıdaki testler bu yolun kapandığını kanıtlıyor.
 */
class JwtUtilTest {

    /** Sızmış olan eski sabit anahtar. Artık hiçbir token'ı doğrulayamamalı. */
    private static final String YANMIS_ESKI_ANAHTAR = "mySuperSecretKeyForJwtGeneration123456789012345";

    private static final String GECERLI_ANAHTAR = "test-ortami-icin-uretilmis-32-byte-uzunlugunda-anahtar";
    private static final long BIR_GUN = 1000L * 60 * 60 * 24;

    private User kullanici(String username) {
        User user = new User();
        user.setUsername(username);
        return user;
    }

    @Test
    void anahtarTanimliDegilse_UygulamaAcilistaHataVerir() {
        IllegalStateException hata = assertThrows(IllegalStateException.class,
                () -> new JwtUtil("", BIR_GUN));

        assertTrue(hata.getMessage().contains("JWT_SECRET"),
                "Hata mesajı hangi ayarın eksik olduğunu söylemeli, mesaj: " + hata.getMessage());
    }

    @Test
    void anahtarCokKisaysa_UygulamaAcilistaHataVerir() {
        // HS256 en az 256 bit (32 byte) anahtar gerektirir.
        assertThrows(IllegalStateException.class,
                () -> new JwtUtil("kisa-anahtar", BIR_GUN));
    }

    @Test
    void sizmisEskiSabitAnahtarlaUretilenToken_ArtikKabulEdilmez() {
        JwtUtil sizdiranTaraf = new JwtUtil(YANMIS_ESKI_ANAHTAR, BIR_GUN);
        JwtUtil uygulama = new JwtUtil(GECERLI_ANAHTAR, BIR_GUN);

        String sahteToken = sizdiranTaraf.generateToken(kullanici("admin"));

        assertFalse(uygulama.validateToken(sahteToken),
                "Eski sabit anahtarla imzalanmış token doğrulanmamalı.");
    }

    @Test
    void baskaBirAnahtarlaImzalananToken_KabulEdilmez() {
        JwtUtil saldirgan = new JwtUtil("saldirganin-kendi-32-byte-uzunlugundaki-anahtari", BIR_GUN);
        JwtUtil uygulama = new JwtUtil(GECERLI_ANAHTAR, BIR_GUN);

        String token = saldirgan.generateToken(kullanici("admin"));

        assertFalse(uygulama.validateToken(token));
    }

    @Test
    void kendiUrettigiTokeniDogrular_VeKullaniciAdiniGeriVerir() {
        JwtUtil uygulama = new JwtUtil(GECERLI_ANAHTAR, BIR_GUN);

        String token = uygulama.generateToken(kullanici("kaanboldan"));

        assertTrue(uygulama.validateToken(token));
        assertEquals("kaanboldan", uygulama.extractUsername(token));
    }

    @Test
    void suresiDolmusToken_KabulEdilmez() throws Exception {
        JwtUtil uygulama = new JwtUtil(GECERLI_ANAHTAR, 1L); // 1 ms
        String token = uygulama.generateToken(kullanici("kaanboldan"));

        Thread.sleep(20);

        assertFalse(uygulama.validateToken(token));

        UserDetails details = org.springframework.security.core.userdetails.User
                .withUsername("kaanboldan").password("x").authorities("ROLE_USER").build();
        assertFalse(uygulama.validateToken(token, details));
    }
}
