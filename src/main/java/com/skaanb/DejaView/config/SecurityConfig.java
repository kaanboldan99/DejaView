package com.skaanb.DejaView.config;

import com.skaanb.DejaView.security.JwtAuthenticationFilter;
import com.skaanb.DejaView.security.JwtUtil;
import com.skaanb.DejaView.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;

    public SecurityConfig(JwtUtil jwtUtil,
                          CustomUserDetailsService customUserDetailsService) {
        this.jwtUtil = jwtUtil;
        this.customUserDetailsService = customUserDetailsService;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, customUserDetailsService);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * TEST / DEVELOPMENT ORTAMI (dev)
     * login olmaya gerek kalmadan tüm endpoint'leri test etmeni sağlar.
     */
    @Bean
    @Profile("dev")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .userDetailsService(customUserDetailsService)
                .authorizeHttpRequests(auth -> auth
                        // dev profilinde tüm isteklere (permitAll) izin veriyoruz
                        .anyRequest().permitAll()
                )
                // Yetkilendirme permitAll olduğu için token'sız istekler yine geçer;
                // ancak token GÖNDERİLDİĞİNDE kullanıcı tanınır. Buna ihtiyaç var:
                // /api/users/me ve ticket sahipliği "kim olduğunu" bilmek zorunda,
                // aksi halde dev'de herkes "anonymous_user" görünür.
                .addFilterBefore(jwtAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * PROD ORTAMI (prod veya default)
     * Canlı ortamda tam güvenlik sağlar, yetkisiz istekleri engeller.
     */
    @Bean
    @Profile({"prod", "default"})
    public SecurityFilterChain prodSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .userDetailsService(customUserDetailsService)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        // Kullanıcı kaydı artık herkese açık değil; sadece ADMIN
                        // yeni kullanıcı ekleyebilir.
                        .requestMatchers("/api/auth/register").hasRole("ADMIN")
                        // AI'a yeniden özetletme, sadece ADMIN yetkisiyle tetiklenebilir.
                        .requestMatchers("/api/tickets/*/resummarize").hasRole("ADMIN")
                        // H2 konsolu bilinçli olarak BURADA YOK: bu zincir profil
                        // verilmediğinde de (default) devreye giriyor, yani üretimde
                        // veritabanı konsolunu herkese açık bırakırdı. Konsol zaten
                        // yalnızca dev profilinde etkin (application-dev.properties).
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}