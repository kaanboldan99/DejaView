package com.skaanb.DejaView.config;

import com.skaanb.DejaView.model.Role;
import com.skaanb.DejaView.model.User;
import com.skaanb.DejaView.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        String adminUsername = "admin";
        String adminEmail = "admin@dejaview.com";
        String adminRawPassword = "admin";

        // Veritabanında admin kullanıcısının zaten var olup olmadığını kontrol ediyoruz
        if (!userRepository.existsByUsername(adminUsername)) {
            User adminUser = new User();
            adminUser.setUsername(adminUsername);
            adminUser.setEmail(adminEmail);
            adminUser.setRole(Role.ADMIN);

            // Güvenlik kurallarınız gereği şifreyi BCryptPasswordEncoder ile hashliyoruz
            adminUser.setPassword(passwordEncoder.encode(adminRawPassword));

            userRepository.save(adminUser);

            logger.info("====================================================");
            logger.info("DejaView: Varsayılan 'admin' kullanıcısı otomatik oluşturuldu!");
            logger.info("Kullanıcı Adı: {}", adminEmail);
            logger.info("Şifre: {}", adminRawPassword);
            logger.info("====================================================");
        } else {
            logger.info("DejaView: 'admin' kullanıcısı veritabanında zaten mevcut.");
        }
    }
}