package com.skaanb.DejaView.service;

import com.skaanb.DejaView.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security ile veritabanı arasındaki köprü.
 *
 * Nasıl çalışır: Spring Security kullanıcıyı kendi {@link UserDetails} tipiyle
 * tanır; bu servis uygulamanın {@link com.skaanb.DejaView.model.User} varlığını
 * o tipe çevirir. Çevrim sırasında rol {@code "ROLE_" + ad} biçimine getirilir
 * çünkü {@code hasRole("ADMIN")} kontrolü tam olarak bu ön eki arar
 * (bkz. {@link com.skaanb.DejaView.config.SecurityConfig}).
 *
 * Kullanıcı her istekte yeniden yüklenir; token'da rol taşınmadığı için yetki
 * değişiklikleri anında etkili olur.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    /** Kullanıcıyı kullanıcı adından bulan depo. */
    private final UserRepository userRepository;

    /**
     * @param userRepository kullanıcı deposu
     */
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Kullanıcıyı adına göre yükleyip Spring Security tipine çevirir.
     *
     * Nasıl çalışır: arama e-posta ile DEĞİL kullanıcı adıyla yapılır, çünkü
     * JWT'nin subject alanı kullanıcı adıdır. Bulunan kayıttan kullanıcı adı,
     * şifre hash'i ve {@code ROLE_} ön ekli yetki üretilir.
     *
     * Kullanıcı yoksa {@link UsernameNotFoundException} fırlatılır; bu, geçerli
     * imzalı ama sahibi silinmiş bir token'da da olur ve çağıran filtre bunu
     * yakalayıp isteği doğrulanmamış olarak sürdürür.
     *
     * @param login aranan kullanıcı adı (JWT subject alanı)
     * @return Spring Security'nin kullandığı kullanıcı nesnesi
     * @throws UsernameNotFoundException bu adla kayıtlı kullanıcı yoksa
     */
    @Override
    public UserDetails loadUserByUsername(String login) {
        return userRepository.findByUsername(login)
                .map(user -> User.withUsername(user.getUsername())
                        .password(user.getPassword())
                        .authorities("ROLE_" + user.getRole().name())
                        .build())
                .orElseThrow(() -> {
                    logger.warn("JWT/login için kullanıcı bulunamadı. username={}", login);
                    return new UsernameNotFoundException("User '" + login + "' not found");
                });
    }
}
