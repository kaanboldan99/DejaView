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
 * Kullanıcının kendi profili. Yalnızca oturum sahibinin verisine erişilir —
 * yol üzerinde kullanıcı adı/id alınmaz, kimlik her zaman JWT'den okunur.
 * Böylece bir kullanıcı başkasının profilini okuyamaz veya değiştiremez.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(Principal principal) {
        String username = requireUsername(principal);

        return userService.getByUsername(username)
                .map(UserProfileResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(@RequestBody UpdateProfileRequest request,
                                                               Principal principal) {
        String username = requireUsername(principal);
        User updated = userService.updateOwnProfile(username, request);
        return ResponseEntity.ok(UserProfileResponse.from(updated));
    }

    /**
     * dev profilinde JWT filtresi çalışsa da token gönderilmemiş olabilir;
     * o durumda kimin profili olduğu bilinemez.
     */
    private String requireUsername(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ProfileUpdateException("Bu işlem için giriş yapmanız gerekiyor.");
        }
        return principal.getName();
    }
}
