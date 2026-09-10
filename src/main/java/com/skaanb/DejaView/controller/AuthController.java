package com.skaanb.DejaView.controller;

import com.skaanb.DejaView.dto.LoginRequest;
import com.skaanb.DejaView.dto.RegisterRequest;
import com.skaanb.DejaView.dto.UserProfileResponse;
import com.skaanb.DejaView.model.User;
import com.skaanb.DejaView.repository.UserRepository;
import com.skaanb.DejaView.security.JwtUtil;
import com.skaanb.DejaView.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Kimlik uçları: kullanıcı kaydı ve giriş.
 *
 * Nasıl çalışır: {@code /api/auth} altında iki uç sunar. Kayıt ucu korumalı
 * ortamda ADMIN'e kısıtlıdır, giriş ucu herkese açıktır
 * (bkz. {@link com.skaanb.DejaView.config.SecurityConfig}).
 *
 * İki uç da varlık nesnesini DOĞRUDAN döndürmez: kayıt
 * {@link UserProfileResponse} ile, giriş yalnızca token taşıyan bir eşleme ile
 * yanıt verir. Varlığın doğrudan döndürülmesi BCrypt şifre hash'ini istemciye
 * ve erişim loglarına sızdırıyordu.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    /** Giriş sırasında kullanıcıyı e-postadan bulmak için depo. */
    @Autowired
    private UserRepository userRepository;

    /** Başarılı girişte token üreten yardımcı. */
    @Autowired
    private JwtUtil jwtUtil;

    /** Girilen şifreyi saklanan hash ile karşılaştıran kodlayıcı. */
    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Kullanıcı oluşturma kurallarını yürüten servis. */
    private final UserService userService;

    /**
     * @param userService kullanıcı oluşturma kurallarını yürüten servis
     */
    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Yeni kullanıcı kaydeder.
     *
     * Nasıl çalışır: gövde ham {@code User} varlığı değil, dar bir DTO ile
     * alınır; istemci ne rolünü ne de kimliğini belirleyebilir. Oluşturma
     * kuralları (benzersizlik kontrolü, şifre hash'leme, rol atama) servise
     * bırakılır. Kural ihlallerinde servis bir çalışma zamanı hatası fırlatır
     * ve burada 400 Bad Request'e çevrilir — kullanıcı hatası olduğu için 500
     * değil.
     *
     * @param request kayıt gövdesi: kullanıcı adı, şifre, e-posta, telefon
     * @return 200 ve oluşturulan profilin güvenli hâli; kural ihlalinde 400 ve
     *         hata mesajı
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            User createdUser = userService.createUser(request);
            logger.info("Yeni kullanıcı kaydedildi. username={}", createdUser.getUsername());
            return ResponseEntity.ok(UserProfileResponse.from(createdUser));
        } catch (RuntimeException e) {
            logger.warn("Kayıt başarısız. username={}, sebep={}", request.getUsername(), e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Kullanıcıyı doğrular ve JWT üretir.
     *
     * Nasıl çalışır: kullanıcı e-postadan bulunur, şifre BCrypt ile
     * karşılaştırılır, başarılıysa token üretilip {@code {"token": "..."}}
     * olarak döner.
     *
     * İki başarısızlık yolu (kayıtlı olmayan e-posta / hatalı şifre) istemciye
     * AYNI mesajla döner. Bu bilinçli: farklı mesajlar, hangi e-postaların
     * sistemde kayıtlı olduğunu dışarıdan tespit etmeye yarardı. Ayrım yalnızca
     * sunucu logunda görünür.
     *
     * @param request giriş gövdesi: e-posta ve düz metin şifre
     * @return 200 ve token; kimlik doğrulanamazsa 401 ve genel hata mesajı
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();

        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            logger.warn("Başarısız giriş denemesi: kayıtlı olmayan e-posta. email={}", email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Geçersiz e-posta veya şifre."));
        }

        User user = optionalUser.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            logger.warn("Başarısız giriş denemesi: hatalı şifre. email={}", email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Geçersiz e-posta veya şifre."));
        }

        String token = jwtUtil.generateToken(user);
        logger.info("Kullanıcı giriş yaptı. username={}", user.getUsername());
        return ResponseEntity.ok(Map.of("token", token));
    }

}
