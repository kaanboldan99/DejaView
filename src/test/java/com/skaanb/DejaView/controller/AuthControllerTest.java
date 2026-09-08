package com.skaanb.DejaView.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaanb.DejaView.dto.LoginRequest;
import com.skaanb.DejaView.model.Role;
import com.skaanb.DejaView.model.User;
import com.skaanb.DejaView.repository.TicketRepository;
import com.skaanb.DejaView.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Login/kayıt uçlarını gerçek HTTP katmanından (MockMvc) test ediyor. Amaç:
// kötücül girdilerin (SQL injection tarzı, XSS tarzı, aşırı uzun string)
// kimlik doğrulamayı atlatmadığını ve uygulamayı çökertmediğini uçtan uca
// kanıtlamak. UserService/UserRepository zaten Spring Data JPA'nın parametreli
// sorgularını kullandığı için klasik SQL injection yapısal olarak mümkün değil;
// bu testler bunu HTTP sınırından itibaren doğruluyor.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setUsername("kaanboldan");
        user.setEmail("kaan@example.com");
        user.setPassword(passwordEncoder.encode("gercekSifre123"));
        userRepository.save(user);
    }

    // ==========================================
    // KONTROL TESTİ: gerçek girdiyle giriş çalışmalı
    // (aşağıdaki güvenlik testlerinin anlamlı olması için önce bunun
    // geçmesi gerekiyor — aksi halde "401 dönüyor" hiçbir şey kanıtlamaz)
    // ==========================================

    @Test
    void testLogin_GercekBilgilerleBasarili_TokenDoner() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("kaan@example.com");
        request.setPassword("gercekSifre123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    // ==========================================
    // SQL INJECTION TARZI PAYLOAD'LAR
    // ==========================================

    @Test
    void testLogin_KlasikOrInjectionEmailde_401DonerTokenVermez() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("' OR '1'='1");
        request.setPassword("herhangi_bir_sey");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void testLogin_KlasikOrInjectionSifrede_401DonerTokenVermez() throws Exception {
        // Given: doğru email ama şifre alanında injection denemesi
        LoginRequest request = new LoginRequest();
        request.setEmail("kaan@example.com");
        request.setPassword("' OR '1'='1' --");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void testLogin_YorumSatiriIleAuthBypassDenemesi_401Doner() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("kaan@example.com'--");
        request.setPassword("herhangi");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLogin_UnionSelectInjectionDenemesi_401Doner() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("x' UNION SELECT * FROM users --");
        request.setPassword("herhangi");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLogin_BosSifreVeInjectionEmail_401DonerCokmez() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("' OR 1=1; --");
        request.setPassword("");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLogin_AsiriUzunEmailPayload_CokmedenKontrolluHataDoner() throws Exception {
        // Given: 20.000 karakterlik email alanı (DoS/stres tarzı)
        LoginRequest request = new LoginRequest();
        request.setEmail("a".repeat(20_000) + "@example.com");
        request.setPassword("herhangi");

        // When & Then: 500 değil, kontrollü bir yanıt (401) bekleniyor
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ==========================================
    // KAYIT (register) UCUNDA KÖTÜCÜL GİRDİ
    // dev profilinde register herkese açık (permitAll); üretimde ADMIN
    // gerektiriyor ama test varsayılan dev profiliyle çalışıyor.
    // ==========================================

    @Test
    void testRegister_SqlInjectionUsername_LiteralOlarakKaydedilirCokmez() throws Exception {
        String body = """
                {"username":"robert'); DROP TABLE users;--","email":"bobby@example.com","password":"password123"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("robert'); DROP TABLE users;--"));

        // Tablo hâlâ ayakta ve önceki kullanıcı hâlâ erişilebilir
        assertLoginStillWorksForExistingUser();
    }

    @Test
    void testRegister_ScriptTagUsername_LiteralOlarakKaydedilir() throws Exception {
        String body = """
                {"username":"<script>alert(1)</script>","email":"xss@example.com","password":"password123"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("<script>alert(1)</script>"));
    }

    // ==========================================
    // KAYIT UCUNDA YETKİ YÜKSELTME / MASS ASSIGNMENT
    //
    // register ucu istemci JSON'unu doğrudan User entity'sine bağlarsa, istemci
    // kendi rolünü ve hatta kendi id'sini belirleyebilir. Aşağıdaki üç test bunun
    // yapılamadığını uçtan uca kanıtlıyor.
    // ==========================================

    @Test
    void testRegister_GovdedeRolADMINGonderilse_KullaniciYineDeUSEROlur() throws Exception {
        String body = """
                {"username":"sinsi","email":"sinsi@example.com","password":"password123","role":"ADMIN"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        User kaydedilen = userRepository.findByUsername("sinsi").orElseThrow();
        assertEquals(Role.USER, kaydedilen.getRole(),
                "Rol istemci gövdesinden belirlenememeli; sunucu her zaman USER atamalı.");
    }

    @Test
    void testRegister_GovdedeMevcutIdGonderilse_VarOlanKullaniciEzilmez() throws Exception {
        User mevcut = userRepository.findByUsername("kaanboldan").orElseThrow();
        Long mevcutId = mevcut.getId();

        String body = """
                {"id":%d,"username":"ezici","email":"ezici@example.com","password":"password123"}
                """.formatted(mevcutId);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // save() bir id ile çağrıldığında INSERT değil UPDATE (merge) yapar;
        // korumasız halde bu, mevcut kullanıcının satırını ezer.
        assertTrue(userRepository.findByUsername("kaanboldan").isPresent(),
                "Var olan kullanıcı, register gövdesine id konarak ezilememeli.");
        assertEquals(mevcutId, userRepository.findByUsername("kaanboldan").orElseThrow().getId());
        assertEquals(2, userRepository.count(), "Yeni kayıt eklenmeli, mevcut kayıt korunmalı.");
    }

    @Test
    void testRegister_YanitSifreHashiniIcermez() throws Exception {
        String body = """
                {"username":"gizli","email":"gizli@example.com","password":"password123"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("gizli"))
                // BCrypt hash'i istemciye dönmemeli; erişim loglarına ve proxy'lere düşer.
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    private void assertLoginStillWorksForExistingUser() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("kaan@example.com");
        request.setPassword("gercekSifre123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }
}
