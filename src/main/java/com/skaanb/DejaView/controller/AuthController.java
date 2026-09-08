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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;


    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Yeni kullanıcı kaydı.
     *
     * Gövde ham {@code User} entity'si değil, dar bir DTO ile alınıyor: istemci
     * ne rolünü ne de id'sini belirleyebilir. Yanıt da entity değil
     * {@link UserProfileResponse} — entity'nin doğrudan döndürülmesi BCrypt
     * şifre hash'ini istemciye ve erişim loglarına sızdırıyordu.
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
