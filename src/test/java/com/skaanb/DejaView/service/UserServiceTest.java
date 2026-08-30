package com.skaanb.DejaView.service;

import com.skaanb.DejaView.dto.UpdateProfileRequest;
import com.skaanb.DejaView.exception.ProfileUpdateException;
import com.skaanb.DejaView.model.User;
import com.skaanb.DejaView.repository.TicketRepository; // 1. Bu importun olduğundan emin olun
import com.skaanb.DejaView.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional // Her testten sonra H2 veritabanındaki değişiklikleri geri alır (rollback)
public class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository; // 2. Eksik olan ve hataya sebep alan enjeksiyon burasıydı

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User existingUser;

    @BeforeEach
    void setUp() {
        // İlişkili tablolar varsa yabancı anahtar (FK) kısıtlamasından ötürü
        // önce bağımlı tablo (Ticket), sonra ana tablo (User) silinmelidir.
        ticketRepository.deleteAll();
        userRepository.deleteAll();

        // Mükerrer kayıt senaryolarını test etmek için veritabanına örnek bir kullanıcı ekliyoruz
        existingUser = new User();
        existingUser.setUsername("kaanboldan");
        existingUser.setEmail("kaan@example.com");
        existingUser.setPassword(passwordEncoder.encode("secret123"));
        existingUser = userRepository.save(existingUser);
    }

    // ==========================================
    // CREATE USER - SUCCESS SENARYOLARI
    // ==========================================

    @Test
    void testCreateUser_Success() {
        // Given
        User newUser = new User();
        newUser.setUsername("newuser");
        newUser.setEmail("newuser@example.com");
        newUser.setPassword("rawPassword123");

        // When
        User savedUser = userService.createUser(newUser);

        // Then
        assertNotNull(savedUser);
        assertNotNull(savedUser.getId());
        assertEquals("newuser", savedUser.getUsername());
        assertEquals("newuser@example.com", savedUser.getEmail());

        // Şifrenin veritabanına açık metin olarak değil, başarıyla hashlenerek kaydedildiğini doğruluyoruz
        assertNotEquals("rawPassword123", savedUser.getPassword());
        assertTrue(passwordEncoder.matches("rawPassword123", savedUser.getPassword()));
    }

    // ==========================================
    // CREATE USER - EXCEPTION & EDGE CASE SENARYOLARI
    // ==========================================

    @Test
    void testCreateUser_ThrowsException_WhenUsernameExists() {
        // Given
        User duplicateUsernameUser = new User();
        duplicateUsernameUser.setUsername("kaanboldan"); // Zaten mevcut olan kullanıcı adı
        duplicateUsernameUser.setEmail("different@example.com");
        duplicateUsernameUser.setPassword("password123");

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.createUser(duplicateUsernameUser);
        });

        assertEquals("Username already taken", exception.getMessage());
    }

    @Test
    void testCreateUser_ThrowsException_WhenEmailExists() {
        // Given
        User duplicateEmailUser = new User();
        duplicateEmailUser.setUsername("differentuser");
        duplicateEmailUser.setEmail("kaan@example.com"); // Zaten mevcut olan e-posta adresi
        duplicateEmailUser.setPassword("password123");

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.createUser(duplicateEmailUser);
        });

        assertEquals("Email already registered", exception.getMessage());
    }

    // ==========================================
    // READ / QUERY - SUCCESS & EMPTY SENARYOLARI
    // ==========================================

    @Test
    void testGetUserById_Success() {
        // When
        Optional<User> foundUser = userService.getUserById(existingUser.getId());

        // Then
        assertTrue(foundUser.isPresent());
        assertEquals(existingUser.getUsername(), foundUser.get().getUsername());
    }

    @Test
    void testGetUserById_NotFound_ReturnsEmptyOptional() {
        // Given
        Long nonExistingId = 99999L;

        // When
        Optional<User> foundUser = userService.getUserById(nonExistingId);

        // Then
        assertFalse(foundUser.isPresent());
    }

    @Test
    void testGetAllUsers_Success() {
        // When
        List<User> users = userService.getAllUsers();

        // Then
        assertNotNull(users);
        assertEquals(1, users.size());
    }

    @Test
    void testGetAllUsers_EmptyDatabase_ReturnsEmptyList() {
        // Given
        ticketRepository.deleteAll();
        userRepository.deleteAll();

        // When
        List<User> users = userService.getAllUsers();

        // Then
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    @Test
    void testGetByUsername_Success() {
        // When
        Optional<User> foundUser = userService.getByUsername("kaanboldan");

        // Then
        assertTrue(foundUser.isPresent());
        assertEquals(existingUser.getId(), foundUser.get().getId());
    }

    @Test
    void testGetByUsername_NotFound_ReturnsEmptyOptional() {
        // When
        Optional<User> foundUser = userService.getByUsername("olmayan_kullanici");

        // Then
        assertFalse(foundUser.isPresent());
    }

    @Test
    void testGetByEmail_Success() {
        // When
        Optional<User> foundUser = userService.getByEmail("kaan@example.com");

        // Then
        assertTrue(foundUser.isPresent());
        assertEquals(existingUser.getUsername(), foundUser.get().getUsername());
    }

    @Test
    void testGetByEmail_NotFound_ReturnsEmptyOptional() {
        // When
        Optional<User> foundUser = userService.getByEmail("yok@example.com");

        // Then
        assertFalse(foundUser.isPresent());
    }

    // ==========================================
    // DELETE - SENARYOLARI
    // ==========================================

    @Test
    void testDeleteUser_Success() {
        // Given
        Long userId = existingUser.getId();

        // When
        userService.deleteUser(userId);

        // Then
        Optional<User> deletedUser = userRepository.findById(userId);
        assertFalse(deletedUser.isPresent());
    }

    @Test
    void testDeleteUser_NonExistingId_ShouldNotThrowException() {
        // Given
        Long nonExistingId = 88888L;

        // When & Then
        assertDoesNotThrow(() -> {
            userService.deleteUser(nonExistingId);
        });
    }

    // ==========================================
    // PROFİL GÜNCELLEME
    // ==========================================

    @Test
    void testUpdateOwnProfile_TelefonEklenir() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhoneNumber("0532 444 55 66");

        User updated = userService.updateOwnProfile("kaanboldan", request);

        // Boşluklar temizlenerek tek biçimde saklanır
        assertEquals("05324445566", updated.getPhoneNumber());
    }

    @Test
    void testUpdateOwnProfile_UluslararasiOnEkKorunur() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhoneNumber("+90 (532) 444-55-66");

        User updated = userService.updateOwnProfile("kaanboldan", request);

        assertEquals("+905324445566", updated.getPhoneNumber());
    }

    @Test
    void testUpdateOwnProfile_BosTelefonNumarayiKaldirir() {
        UpdateProfileRequest ekle = new UpdateProfileRequest();
        ekle.setPhoneNumber("05324445566");
        userService.updateOwnProfile("kaanboldan", ekle);

        UpdateProfileRequest kaldir = new UpdateProfileRequest();
        kaldir.setPhoneNumber("");
        User updated = userService.updateOwnProfile("kaanboldan", kaldir);

        // Boş string değil null yazılmalı: unique sütunda birden fazla boş
        // string olamaz ama birden fazla NULL olabilir.
        assertNull(updated.getPhoneNumber());
    }

    @Test
    void testUpdateOwnProfile_GonderilmeyenAlanDegismez() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhoneNumber("05324445566");

        User updated = userService.updateOwnProfile("kaanboldan", request);

        // email gönderilmediği için dokunulmamalı
        assertEquals("kaan@example.com", updated.getEmail());
    }

    @Test
    void testUpdateOwnProfile_GecersizTelefonReddedilir() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhoneNumber("abc123");

        assertThrows(ProfileUpdateException.class,
                () -> userService.updateOwnProfile("kaanboldan", request));
    }

    @Test
    void testUpdateOwnProfile_CokKisaTelefonReddedilir() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhoneNumber("12345");

        assertThrows(ProfileUpdateException.class,
                () -> userService.updateOwnProfile("kaanboldan", request));
    }

    @Test
    void testUpdateOwnProfile_BosEpostaReddedilir() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("   ");

        assertThrows(ProfileUpdateException.class,
                () -> userService.updateOwnProfile("kaanboldan", request));
    }

    @Test
    void testUpdateOwnProfile_BaskasininEpostasiReddedilir() {
        User digeri = new User();
        digeri.setUsername("digeri");
        digeri.setEmail("digeri@example.com");
        digeri.setPassword(passwordEncoder.encode("secret123"));
        userRepository.save(digeri);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("digeri@example.com");

        assertThrows(ProfileUpdateException.class,
                () -> userService.updateOwnProfile("kaanboldan", request));
    }

    @Test
    void testUpdateOwnProfile_BaskasininTelefonuReddedilir() {
        User digeri = new User();
        digeri.setUsername("digeri");
        digeri.setEmail("digeri@example.com");
        digeri.setPassword(passwordEncoder.encode("secret123"));
        digeri.setPhoneNumber("05324445566");
        userRepository.save(digeri);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhoneNumber("0532 444 55 66");

        assertThrows(ProfileUpdateException.class,
                () -> userService.updateOwnProfile("kaanboldan", request));
    }

    @Test
    void testUpdateOwnProfile_KendiTelefonunuTekrarKaydedebilir() {
        UpdateProfileRequest ilk = new UpdateProfileRequest();
        ilk.setPhoneNumber("05324445566");
        userService.updateOwnProfile("kaanboldan", ilk);

        // Aynı numarayı tekrar göndermek "başkasında kayıtlı" hatası vermemeli
        UpdateProfileRequest ikinci = new UpdateProfileRequest();
        ikinci.setPhoneNumber("0532 444 55 66");

        assertDoesNotThrow(() -> userService.updateOwnProfile("kaanboldan", ikinci));
    }

    @Test
    void testUpdateOwnProfile_OlmayanKullaniciReddedilir() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhoneNumber("05324445566");

        assertThrows(ProfileUpdateException.class,
                () -> userService.updateOwnProfile("olmayan_kullanici", request));
    }

    // ==========================================
    // GÜVENLİK: SQL INJECTION VE KÖTÜCÜL GİRDİ SENARYOLARI
    //
    // Spring Data JPA'nın türetilmiş sorgu metotları (findByUsername vb.)
    // arka planda parametreli (PreparedStatement) sorgu üretir; bu yüzden
    // klasik string-concatenation SQL injection burada yapısal olarak mümkün
    // değil. Bu testler bunu somut olarak kanıtlıyor ve ileride biri
    // (yanlışlıkla) native/concatenation tabanlı bir sorguya geçerse
    // regresyonu yakalıyor.
    // ==========================================

    @Test
    void testGetByUsername_KlasikSqlInjectionPayload_BosDoner() {
        // Given: klasik "her satırı getir" denemesi
        String payload = "' OR '1'='1";

        // When
        Optional<User> result = userService.getByUsername(payload);

        // Then: injection çalışsaydı existingUser dönerdi; parametreli sorgu
        // sayesinde payload'ın ta kendisiyle literal eşleşme aranıyor, bulunamıyor
        assertFalse(result.isPresent());
    }

    @Test
    void testGetByUsername_YorumSatiriIleTablodusurmeDenemesi_BosDoner() {
        String payload = "kaanboldan'; DROP TABLE users; --";

        Optional<User> result = userService.getByUsername(payload);

        assertFalse(result.isPresent());
        // Tablo gerçekten düşürülseydi bu satır bile exception fırlatırdı
        assertEquals(1, userRepository.count());
    }

    @Test
    void testGetByEmail_UnionSelectInjectionPayload_BosDoner() {
        String payload = "x' UNION SELECT * FROM users --";

        Optional<User> result = userService.getByEmail(payload);

        assertFalse(result.isPresent());
    }

    @Test
    void testCreateUser_SqlInjectionPayloadUsername_LiteralOlarakKaydedilir() {
        // Given
        User user = new User();
        user.setUsername("robert'); DROP TABLE users;--");
        user.setEmail("bobby-tables@example.com");
        user.setPassword("password123");

        // When
        User saved = userService.createUser(user);

        // Then: payload aynen (kaçışsız, ama zararsız) bir string olarak saklanıyor,
        // tablo hâlâ ayakta ve mevcut kullanıcı hâlâ orada
        assertEquals("robert'); DROP TABLE users;--", saved.getUsername());
        assertTrue(userRepository.findByUsername("kaanboldan").isPresent());
        assertEquals(2, userRepository.count());
    }

    @Test
    void testUpdateOwnProfile_SqlInjectionPayloadEmail_LiteralOlarakSaklanir() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("a' OR '1'='1@example.com");

        User updated = userService.updateOwnProfile("kaanboldan", request);

        assertEquals("a' OR '1'='1@example.com", updated.getEmail());
        // Diğer kullanıcı verisi injection'dan etkilenmemiş olmalı
        assertEquals(1, userRepository.count());
    }

    @Test
    void testCreateUser_AsiriUzunUsername_KontrollucBirHataVerir() {
        // Given: 10.000 karakterlik bir username (buffer/DoS tarzı stres testi)
        User user = new User();
        user.setUsername("a".repeat(10_000));
        user.setEmail("uzun@example.com");
        user.setPassword("password123");

        // When & Then: uygulama çökmemeli (500/StackOverflow değil), kontrollü
        // bir exception ile (DB sütun sınırı vb.) sonuçlanmalı ya da başarılı olmalı —
        // ikisi de kabul edilebilir, önemli olan uncaught bir crash olmaması
        assertDoesNotThrow(() -> {
            try {
                userService.createUser(user);
            } catch (RuntimeException expectedPossible) {
                // DB seviyesinde sütun sınırı aşımı gibi kontrollü bir hata kabul edilir
            }
        });
    }

    @Test
    void testGetByUsername_NullByteIcerenPayload_CokmedenBosDoner() {
        // Given: bazı native sürücülerde/kütüphanelerde sorun çıkarabilen null byte
        String payload = "kaanboldan\0' OR '1'='1";

        // When & Then
        assertDoesNotThrow(() -> {
            Optional<User> result = userService.getByUsername(payload);
            assertFalse(result.isPresent());
        });
    }

    @Test
    void testCreateUser_ScriptTagIcerenUsername_LiteralOlarakSaklanirCalistirlmaz() {
        // Given: XSS tarzı payload — DB katmanında "çalıştırılmaz", sadece
        // metin olarak saklanır; render eden taraf (frontend) escape etmeli.
        User user = new User();
        user.setUsername("<script>alert(1)</script>");
        user.setEmail("xss@example.com");
        user.setPassword("password123");

        User saved = userService.createUser(user);

        assertEquals("<script>alert(1)</script>", saved.getUsername());
    }
}