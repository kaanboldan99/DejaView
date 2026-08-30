package com.skaanb.DejaView.service;

import com.skaanb.DejaView.dto.UpdateProfileRequest;
import com.skaanb.DejaView.exception.ProfileUpdateException;
import com.skaanb.DejaView.model.User;
import com.skaanb.DejaView.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Kullanıcı oluştur
    public User createUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already taken");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Şifreyi hashle
        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);

        return userRepository.save(user);
    }

    // Diğer metotlar aynı kalabilir
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> getByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
        logger.info("Kullanıcı silindi. id={}", id);
    }

    /**
     * Kullanıcının kendi profilini günceller.
     *
     * Yalnızca gönderilen (null olmayan) alanlar değiştirilir; böylece istemci
     * tek bir alanı güncellemek için tüm profili göndermek zorunda kalmaz.
     * phoneNumber için boş string gönderilmesi "telefonu kaldır" anlamına gelir.
     *
     * email ve phoneNumber veritabanında unique olduğu için, kayıt sırasında
     * DataIntegrityViolationException almak yerine burada önden kontrol edilip
     * anlamlı bir hata mesajı döndürülür.
     */
    @Transactional
    public User updateOwnProfile(String username, UpdateProfileRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ProfileUpdateException("Kullanıcı bulunamadı."));

        if (request.getEmail() != null) {
            String email = request.getEmail().trim();
            if (email.isEmpty()) {
                throw new ProfileUpdateException("E-posta boş bırakılamaz.");
            }
            if (!email.equalsIgnoreCase(user.getEmail())) {
                userRepository.findByEmail(email).ifPresent(other -> {
                    if (!other.getId().equals(user.getId())) {
                        throw new ProfileUpdateException("Bu e-posta başka bir hesapta kayıtlı.");
                    }
                });
                user.setEmail(email);
            }
        }

        if (request.getPhoneNumber() != null) {
            String phone = normalizePhone(request.getPhoneNumber());

            if (phone == null) {
                // Boş gönderim telefonu kaldırır. unique sütunda birden fazla
                // NULL'a izin verilir, boş string'e verilmez; bu yüzden null yazıyoruz.
                user.setPhoneNumber(null);
            } else {
                if (!phone.equals(user.getPhoneNumber())) {
                    userRepository.findByPhoneNumber(phone).ifPresent(other -> {
                        if (!other.getId().equals(user.getId())) {
                            throw new ProfileUpdateException("Bu telefon numarası başka bir hesapta kayıtlı.");
                        }
                    });
                }
                user.setPhoneNumber(phone);
            }
        }

        User saved = userRepository.save(user);
        logger.info("Profil güncellendi. username={}", username);
        return saved;
    }

    /**
     * Telefon numarasını karşılaştırılabilir tek bir biçime indirger:
     * boşluk, tire, parantez temizlenir; baştaki + korunur.
     * Boş girdi için null döner (telefonu kaldırma anlamına gelir).
     */
    private String normalizePhone(String raw) {
        String cleaned = raw.replaceAll("[\\s()\\-./]", "");
        if (cleaned.isEmpty()) {
            return null;
        }

        boolean international = cleaned.startsWith("+");
        String digits = international ? cleaned.substring(1) : cleaned;

        if (!digits.matches("\\d+")) {
            throw new ProfileUpdateException("Telefon numarası yalnızca rakam içerebilir.");
        }
        if (digits.length() < 7 || digits.length() > 15) {
            throw new ProfileUpdateException("Telefon numarası 7-15 hane arasında olmalıdır.");
        }

        return international ? "+" + digits : digits;
    }
}
