package com.skaanb.DejaView.service;

import com.skaanb.DejaView.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


    @Service
    public class CustomUserDetailsService implements UserDetailsService {

        private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

        private final UserRepository userRepository;

        public CustomUserDetailsService(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @Override
        public UserDetails loadUserByUsername(String login) {
            return userRepository.findByUsername(login)              // ← use username
                    .map(user -> User.withUsername(user.getUsername())
                            .password(user.getPassword())
                            .authorities("ROLE_" + user.getRole().name())
                            .build())
                    .orElseThrow(() -> {
                        logger.warn("JWT/login için kullanıcı bulunamadı. username={}", login);
                        return new UsernameNotFoundException("User '" + login + "' not found");
                    });
        }
    }


