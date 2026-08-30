package com.skaanb.DejaView.dto;

import com.skaanb.DejaView.model.Role;
import com.skaanb.DejaView.model.User;

import java.time.LocalDateTime;

/**
 * Kullanıcının kendi profil bilgisi.
 *
 * Şifre bu DTO'da bilinçli olarak yok: User entity'sini doğrudan döndürmek
 * hash'lenmiş şifreyi de istemciye gönderir.
 */
public class UserProfileResponse {

    private Long id;
    private String username;
    private String email;
    private String phoneNumber;
    private Role role;
    private LocalDateTime createdAt;

    public static UserProfileResponse from(User user) {
        UserProfileResponse dto = new UserProfileResponse();
        dto.id = user.getId();
        dto.username = user.getUsername();
        dto.email = user.getEmail();
        dto.phoneNumber = user.getPhoneNumber();
        dto.role = user.getRole();
        dto.createdAt = user.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
