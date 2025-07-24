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

    // Username var mı yok mu kontrolü (örneğin kayıt öncesi)
    boolean existsByUsername(String username);

    // Email var mı yok mu kontrolü
    boolean existsByEmail(String email);
}
