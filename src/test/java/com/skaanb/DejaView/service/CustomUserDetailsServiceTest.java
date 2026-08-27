package com.skaanb.DejaView.service;

import com.skaanb.DejaView.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class) // Mockito kütüphanesini JUnit 5 ile entegre eder
public class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private com.skaanb.DejaView.model.User mockDbUser;

    @BeforeEach
    void setUp() {
        // Her test senaryosu için veritabanından dönecek sahte (mock) Entity nesnesini hazırlıyoruz
        mockDbUser = new com.skaanb.DejaView.model.User();
        mockDbUser.setUsername("kaanboldan");
        mockDbUser.setPassword("encoded_password_123");
        mockDbUser.setEmail("kaan@example.com");
    }

    @Test
    void testLoadUserByUsername_Success() {
        // Given (Ön Koşullar)
        String username = "kaanboldan";

        // Veritabanı sorgulandığında hazırladığımız sahte kullanıcı nesnesini dönmesini söylüyoruz
        Mockito.when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(mockDbUser));

        // When (Eylem)
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

        // Then (Doğrulama)
        assertNotNull(userDetails);
        assertEquals("kaanboldan", userDetails.getUsername());
        assertEquals("encoded_password_123", userDetails.getPassword());

        // Spring Security için eklediğiniz varsayılan rolü kontrol ediyoruz
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void testLoadUserByUsername_AdminRole_GrantsAdminAuthority() {
        // Given
        mockDbUser.setRole(com.skaanb.DejaView.model.Role.ADMIN);
        Mockito.when(userRepository.findByUsername("kaanboldan"))
                .thenReturn(Optional.of(mockDbUser));

        // When
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("kaanboldan");

        // Then
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertFalse(userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void testLoadUserByUsername_ThrowsException_WhenUserNotFound() {
        // Given
        String unknownUser = "bilinmeyen_kullanici";

        // Veritabanında kullanıcı bulunamadığında boş (Optional.empty) dönmesini söylüyoruz
        Mockito.when(userRepository.findByUsername(unknownUser))
                .thenReturn(Optional.empty());

        // When & Then
        // Kullanıcı yoksa UsernameNotFoundException fırlatılmasını bekliyoruz
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () -> {
            customUserDetailsService.loadUserByUsername(unknownUser);
        });

        // Fırlatılan hata mesajının doğruluğunu kontrol ediyoruz
        assertEquals("User '" + unknownUser + "' not found", exception.getMessage());
    }
}