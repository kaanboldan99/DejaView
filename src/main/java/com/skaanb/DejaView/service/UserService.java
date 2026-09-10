package com.skaanb.DejaView.service;

import com.skaanb.DejaView.dto.RegisterRequest;
import com.skaanb.DejaView.dto.UpdateProfileRequest;
import com.skaanb.DejaView.exception.ProfileUpdateException;
import com.skaanb.DejaView.model.Role;
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

/**
 * Kullanıcı iş kuralları: oluşturma, sorgulama, profil güncelleme.
 *
 * Nasıl çalışır: controller katmanı yalnızca isteği alır; benzersizlik
 * kontrolleri, şifre hash'leme, rol atama ve telefon normalizasyonu gibi
 * kuralların hepsi burada uygulanır.
 *
 * Sınıfın taşıdığı iki güvenlik kararı: istemciden gelen nesne asla doğrudan
 * kaydedilmez (yalnızca izin verilen alanlar kopyalanır) ve rol her zaman
 * sunucuda atanır.
 */
@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    /** Kullanıcı okuma/yazma deposu. */
    private final UserRepository userRepository;

    /** Şifreleri BCrypt ile hash'leyen kodlayıcı. */
    private final PasswordEncoder passwordEncoder;

    /**
     * @param userRepository  kullanıcı deposu
     * @param passwordEncoder şifre hash'leyici
     */
    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Yeni kullanıcı oluşturur.
     *
     * Nasıl çalışır: önce kullanıcı adı ve e-posta benzersizliği kontrol
     * edilir; sonra SIFIRDAN bir {@link User} kurulur ve yalnızca izin verilen
     * alanlar kopyalanır. İstemciden gelen nesne DOĞRUDAN kaydedilmez —
     * böylece istemci ne kendi rolünü (her zaman {@link Role#USER}) ne de kendi
     * kimliğini belirleyebilir. Kimliğin istemciden gelmesi, var olan bir
     * kullanıcının satırının ezilmesine yol açıyordu
     * (bkz. {@link RegisterRequest}).
     *
     * Şifre burada hash'lenir; düz metin hiçbir yerde saklanmaz.
     *
     * @param request kayıt gövdesi: kullanıcı adı, şifre, e-posta, telefon
     * @return kaydedilmiş kullanıcı (kimliği atanmış hâlde)
     * @throws RuntimeException kullanıcı adı ya da e-posta zaten kayıtlıysa
     */
    public User createUser(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        /* Rol istemciden ASLA alınmaz. */
        user.setRole(Role.USER);

        return userRepository.save(user);
    }

    /**
     * Kullanıcıyı kimliğine göre bulur.
     *
     * @param id aranan kullanıcının veritabanı kimliği
     * @return kullanıcı; kayıt yoksa boş {@link Optional}
     */
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Tüm kullanıcıları döner.
     *
     * Nasıl çalışır: sayfalama yoktur; kullanıcı sayısı büyüdüğünde bu metodun
     * sayfalı bir sürümle değiştirilmesi gerekir.
     *
     * @return kayıtlı tüm kullanıcılar
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Kullanıcıyı adına göre bulur.
     *
     * Nasıl çalışır: JWT'nin subject alanı kullanıcı adı olduğu için profil
     * uçları kimliği bu metotla çözer.
     *
     * @param username aranan kullanıcı adı
     * @return kullanıcı; kayıt yoksa boş {@link Optional}
     */
    public Optional<User> getByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Kullanıcıyı e-postasına göre bulur.
     *
     * @param email aranan e-posta
     * @return kullanıcı; kayıt yoksa boş {@link Optional}
     */
    public Optional<User> getByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Kullanıcıyı siler.
     *
     * Nasıl çalışır: {@link User} üzerindeki ilişki {@code cascade = ALL}
     * olduğu için kullanıcının kayıtları da birlikte silinir.
     *
     * @param id silinecek kullanıcının kimliği
     */
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
        logger.info("Kullanıcı silindi. id={}", id);
    }

    /**
     * Kullanıcının kendi profilini günceller.
     *
     * Nasıl çalışır: yalnızca gönderilen ({@code null} olmayan) alanlar
     * değiştirilir; böylece istemci tek bir alanı güncellemek için tüm profili
     * göndermek zorunda kalmaz. {@code phoneNumber} için BOŞ string
     * gönderilmesi "telefonu kaldır" anlamına gelir.
     *
     * E-posta ve telefon veritabanında benzersiz olduğu için, kayıt sırasında
     * anlaşılmaz bir veritabanı kısıt hatası almak yerine çakışma burada önden
     * kontrol edilip anlamlı bir hata mesajı döndürülür. Kontrol, değer
     * GERÇEKTEN değişiyorsa yapılır — kullanıcının kendi mevcut e-postasını
     * tekrar göndermesi çakışma sayılmamalı.
     *
     * {@code @Transactional}: okuma, doğrulama ve yazma tek bir işlem olarak
     * yürür; doğrulama ortasında hata çıkarsa yarım kalmış bir güncelleme
     * kalmaz.
     *
     * @param username güncellenecek profilin sahibi (JWT'den gelir)
     * @param request  değiştirilecek alanlar; gönderilmeyenler korunur
     * @return kaydedilmiş, güncel kullanıcı
     * @throws ProfileUpdateException kullanıcı yoksa, e-posta boş gönderildiyse,
     *                                değer başka bir hesapta kayıtlıysa ya da
     *                                telefon biçimi geçersizse
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
                /*
                 * Boş gönderim telefonu kaldırır. Benzersiz sütunda birden fazla
                 * NULL'a izin verilir, boş string'e verilmez; bu yüzden null yazılıyor.
                 */
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
     * Telefon numarasını karşılaştırılabilir tek bir biçime indirger.
     *
     * Nasıl çalışır: boşluk, tire, parantez, nokta ve eğik çizgi temizlenir;
     * baştaki {@code +} korunur. Normalizasyon şart çünkü benzersizlik kontrolü
     * birebir metin karşılaştırmasıyla yapılıyor — aksi halde
     * {@code "0532 111 22 33"} ile {@code "05321112233"} farklı iki numara
     * sayılırdı.
     *
     * Temizlikten sonra kalan kısım yalnızca rakamlardan oluşmalı ve 7-15 hane
     * arasında olmalıdır; bu aralık uluslararası numaralandırma standardının
     * pratik sınırlarıdır.
     *
     * @param raw istemciden gelen ham numara
     * @return normalize edilmiş numara; girdi temizlik sonrası boşsa
     *         {@code null} (telefonu kaldırma anlamına gelir)
     * @throws ProfileUpdateException rakam dışı karakter varsa ya da hane sayısı
     *                                aralık dışındaysa
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
