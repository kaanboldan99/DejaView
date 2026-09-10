package com.skaanb.DejaView.config;

import com.skaanb.DejaView.model.Role;
import com.skaanb.DejaView.model.User;
import com.skaanb.DejaView.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Uygulama açılışında ilk admin hesabını oluşturur.
 *
 * Nasıl çalışır: {@link CommandLineRunner} uyguladığı için Spring bağlamı
 * hazır olduğunda {@link #run(String...)} bir kez çalıştırılır. Hesap zaten
 * varsa hiçbir şey yapılmaz, yani her açılışta güvenle çalışır.
 *
 * Şifre ARTIK KODDA SABİT DEĞİL. Önceden her profilde — üretim dâhil —
 * {@code admin}/{@code admin} hesabı açılıyor ve şifre INFO seviyesinde log
 * dosyasına düz metin yazılıyordu. Artık şifre {@code dejaview.admin.password}
 * (üretimde {@code ADMIN_PASSWORD} ortam değişkeni) üzerinden geliyor; ayar
 * boşsa hiç hesap oluşturulmaz. Böylece "kurulumu unutulmuş" bir sunucuda
 * tahmin edilebilir bir admin hesabı kalmıyor.
 *
 * dev profilinde şifre {@code application-dev.properties} içinden geliyor,
 * yani yerel geliştirme akışı değişmiyor.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    /** Kullanıcı arama ve kaydetme için depo. */
    private final UserRepository userRepository;

    /** Şifreyi BCrypt ile hash'leyen kodlayıcı. */
    private final PasswordEncoder passwordEncoder;

    /** Oluşturulacak admin hesabının kullanıcı adı; varsayılan {@code admin}. */
    private final String adminUsername;

    /** Oluşturulacak admin hesabının e-postası. */
    private final String adminEmail;

    /** Admin şifresi; BOŞ bırakılırsa hesap hiç oluşturulmaz. */
    private final String adminPassword;

    /**
     * Bağımlılıkları ve admin hesabı ayarlarını alır.
     *
     * Nasıl çalışır: ayarların hepsinin varsayılanı var, ama şifrenin
     * varsayılanı bilinçli olarak BOŞ — tanımlanmadığında hesap oluşturulmaz.
     *
     * @param userRepository  kullanıcı deposu
     * @param passwordEncoder şifre hash'leyici
     * @param adminUsername   {@code dejaview.admin.username}; varsayılan {@code admin}
     * @param adminEmail      {@code dejaview.admin.email}; varsayılan {@code admin@dejaview.com}
     * @param adminPassword   {@code dejaview.admin.password}; varsayılanı boş
     */
    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${dejaview.admin.username:admin}") String adminUsername,
                           @Value("${dejaview.admin.email:admin@dejaview.com}") String adminEmail,
                           @Value("${dejaview.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    /**
     * Admin hesabını gerekiyorsa oluşturur.
     *
     * Nasıl çalışır: üç yoldan biri işler — (1) hesap zaten varsa bilgi
     * loglanıp çıkılır; (2) şifre tanımlı değilse UYARI loglanıp hesap
     * oluşturulmaz (uygulama yine de açılır, sadece admin girişi olmaz);
     * (3) ikisi de değilse hesap {@link Role#ADMIN} yetkisiyle, şifresi
     * hash'lenerek kaydedilir. Şifre hiçbir yolda loglanmaz.
     *
     * @param args komut satırı argümanları; bu uygulama kullanmıyor
     */
    @Override
    public void run(String... args) {
        if (userRepository.existsByUsername(adminUsername)) {
            logger.info("DejaView: '{}' kullanıcısı veritabanında zaten mevcut.", adminUsername);
            return;
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            logger.warn("DejaView: başlangıç admin hesabı OLUŞTURULMADI — ADMIN_PASSWORD "
                    + "(dejaview.admin.password) tanımlı değil. Hesabı oluşturmak için bu değişkeni "
                    + "set edip uygulamayı yeniden başlatın.");
            return;
        }

        User adminUser = new User();
        adminUser.setUsername(adminUsername);
        adminUser.setEmail(adminEmail);
        adminUser.setRole(Role.ADMIN);
        adminUser.setPassword(passwordEncoder.encode(adminPassword));

        userRepository.save(adminUser);

        /* Şifre bilinçli olarak loglanmıyor. */
        logger.info("DejaView: başlangıç admin hesabı oluşturuldu. username={}, email={}",
                adminUsername, adminEmail);
    }
}
