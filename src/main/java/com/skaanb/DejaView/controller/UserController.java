package com.skaanb.DejaView.controller;

import com.skaanb.DejaView.dto.UpdateProfileRequest;
import com.skaanb.DejaView.dto.UserProfileResponse;
import com.skaanb.DejaView.exception.ProfileUpdateException;
import com.skaanb.DejaView.model.User;
import com.skaanb.DejaView.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Kullanıcının kendi profilini okuduğu ve güncellediği uçlar.
 *
 * Nasıl çalışır: yol üzerinde kullanıcı adı ya da kimlik ALINMAZ; kimlik her
 * zaman JWT'den okunur ({@link Principal}). Böylece bir kullanıcının
 * başkasının profilini okuması veya değiştirmesi yapısal olarak imkânsızdır —
 * yetki kontrolü ayrı bir adım değil, tasarımın kendisi.
 *
 * Yanıtlar {@link UserProfileResponse} ile verilir, yani şifre hash'i hiçbir
 * yolda dışarı çıkmaz.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    /** Profil okuma ve güncelleme kurallarını yürüten servis. */
    private final UserService userService;

    /**
     * @param userService profil işlemlerini yürüten servis
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Oturum sahibinin profilini döner.
     *
     * Nasıl çalışır: kullanıcı adı token'dan alınır ve profil o adla yüklenir.
     * Kullanıcı bulunamazsa 404 döner — token geçerli imzalı olsa bile hesap
     * silinmiş olabilir.
     *
     * @param principal Spring'in JWT filtresinden doldurduğu kimlik; oturum
     *                  yoksa {@code null} olabilir
     * @return 200 ve profil; kullanıcı bulunamazsa 404
     * @throws ProfileUpdateException oturum yoksa (400'e çevrilir)
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(Principal principal) {
        String username = requireUsername(principal);

        return userService.getByUsername(username)
                .map(UserProfileResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Oturum sahibinin profilini günceller.
     *
     * Nasıl çalışır: yalnızca gövdede gönderilen alanlar değiştirilir; çakışma
     * ve biçim kontrolleri serviste yapılır ve ihlalde
     * {@link ProfileUpdateException} fırlatılır — bu tip
     * {@link com.skaanb.DejaView.exception.GlobalExceptionHandler} tarafından
     * 400'e çevrilir, 500 hata kaydı oluşturulmaz.
     *
     * @param request   güncellenecek alanlar; gönderilmeyenler korunur
     * @param principal JWT'den gelen kimlik
     * @return 200 ve güncellenmiş profilin güvenli hâli
     * @throws ProfileUpdateException oturum yoksa veya doğrulama başarısızsa
     */
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(@RequestBody UpdateProfileRequest request,
                                                               Principal principal) {
        String username = requireUsername(principal);
        User updated = userService.updateOwnProfile(username, request);
        return ResponseEntity.ok(UserProfileResponse.from(updated));
    }

    /**
     * Kimliği çıkarır; oturum yoksa anlamlı bir hata fırlatır.
     *
     * Nasıl çalışır: dev profilinde JWT filtresi çalışsa da token
     * gönderilmemiş olabilir; o durumda kimin profili olduğu bilinemez ve
     * {@code principal} boş gelir. Bu kontrol olmasaydı burada
     * {@code NullPointerException} oluşup 500 dönerdi.
     *
     * @param principal JWT filtresinden gelen kimlik; {@code null} olabilir
     * @return oturum sahibinin kullanıcı adı
     * @throws ProfileUpdateException kimlik yoksa veya adı boşsa
     */
    private String requireUsername(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ProfileUpdateException("Bu işlem için giriş yapmanız gerekiyor.");
        }
        return principal.getName();
    }
}
