package com.skaanb.DejaView.service;

import com.skaanb.DejaView.model.User;
import com.skaanb.DejaView.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Kullanıcı oluştur
    public User createUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already taken");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Burada parola hashleme işlemi yapılabilir (örn. BCrypt)
        return userRepository.save(user);
    }

    // ID ile kullanıcı getir
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    // Tüm kullanıcıları getir
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // Kullanıcı adı ile ara
    public Optional<User> getByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    // Email ile ara
    public Optional<User> getByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    // Sil
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
