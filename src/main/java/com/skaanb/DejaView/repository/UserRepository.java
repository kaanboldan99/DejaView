package com.skaanb.DejaView.repository;

import com.skaanb.DejaView.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Kullanıcıyı username'e göre bul
    Optional<User> findByUsername(String username);

    // Kullanıcıyı email'e göre bul
    Optional<User> findByEmail(String email);

    // Telefon numarası unique; profil güncellemede çakışma kontrolü için
    Optional<User> findByPhoneNumber(String phoneNumber);

    // Username var mı yok mu kontrolü (örneğin kayıt öncesi)
    boolean existsByUsername(String username);

    // Email var mı yok mu kontrolü
    boolean existsByEmail(String email);
}
