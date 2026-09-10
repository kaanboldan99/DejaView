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

/**
 * Spring Security yapılandırması: hangi ucun kime açık olduğu burada belirlenir.
 *
 * Nasıl çalışır: İKİ ayrı filtre zinciri tanımlı ve aktif profile göre yalnızca
 * biri oluşturulur — dev profilinde gevşek zincir, {@code prod} ve
 * {@code default} profillerinde korumalı zincir.
 *
 * {@code default}'un korumalı zincire dâhil olması bilinçli: profil hiç
 * verilmediğinde Spring "default" profilini aktif eder. Kapsam yalnızca
 * {@code prod} olsaydı, deploy sırasında profili vermeyi unutmak tüm uçları
 * kimlik doğrulamasız açardı — üstelik sessizce.
 *
 * Her iki zincir de oturumsuz ({@code STATELESS}) çalışır: kimlik her istekte
 * JWT'den yeniden kurulur, sunucuda oturum tutulmaz.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Token doğrulama yardımcısı; JWT filtresine verilir. */
    private final JwtUtil jwtUtil;

    /** Kullanıcı ve yetkilerini yükleyen servis. */
    private final CustomUserDetailsService customUserDetailsService;

    /**
     * @param jwtUtil                  token çözme/doğrulama yardımcısı
     * @param customUserDetailsService kullanıcı ve yetki bilgisini yükleyen servis
     */
    public SecurityConfig(JwtUtil jwtUtil,
                          CustomUserDetailsService customUserDetailsService) {
        this.jwtUtil = jwtUtil;
        this.customUserDetailsService = customUserDetailsService;
    }

    /**
     * JWT doğrulama filtresini bean olarak sunar.
     *
     * Nasıl çalışır: her iki zincir de bu bean'i kullanır; filtre isteği
     * reddetmez, yalnızca geçerli token varsa kimliği güvenlik bağlamına yazar.
     *
     * @return zincire eklenecek JWT filtresi
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, customUserDetailsService);
    }

    /**
     * Şifre hash'leme algoritmasını belirler.
     *
     * Nasıl çalışır: BCrypt her hash'e rastgele bir tuz gömer, bu yüzden aynı
     * şifre her kayıtta farklı bir hash üretir ve karşılaştırma düz eşitlikle
     * değil {@code matches()} ile yapılır.
     *
     * @return uygulama genelinde kullanılan şifre kodlayıcı
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * TEST / GELİŞTİRME ZİNCİRİ (dev profili).
     *
     * Nasıl çalışır: tüm isteklere izin verilir ({@code permitAll}), böylece
     * uçlar giriş yapmadan denenebilir. Buna rağmen JWT filtresi zincire yine
     * de ekleniyor: yetkilendirme serbest olduğu için token'sız istekler geçer,
     * ancak token GÖNDERİLDİĞİNDE kullanıcı tanınır. Buna ihtiyaç var çünkü
     * {@code /api/users/me} ve kayıt sahipliği "kim olduğunu" bilmek zorunda;
     * aksi halde dev ortamında herkes "anonymous_user" görünürdü.
     *
     * @param http Spring'in sağladığı güvenlik yapılandırma nesnesi
     * @return dev profiline özel, korumasız filtre zinciri
     * @throws Exception yapılandırma sırasında oluşan hata
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
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * KORUMALI ZİNCİR (prod ve default profilleri).
     *
     * Nasıl çalışır: kurallar yukarıdan aşağı, İLK EŞLEŞEN kazanır sırasıyla
     * değerlendirilir ve en sonda {@code anyRequest().authenticated()} durur —
     * yani açıkça serbest bırakılmayan her uç kimlik doğrulaması ister.
     * Serbest bırakılanlar yalnızca giriş ucu ve API dokümantasyonu;
     * yönetimsel uçlar ADMIN rolüne kısıtlıdır.
     *
     * Kayıt ucu ({@code /api/auth/register}) bilinçli olarak herkese açık
     * değil: yeni kullanıcıyı yalnızca ADMIN ekleyebilir.
     *
     * H2 konsolu burada YOK: bu zincir profil verilmediğinde de devreye
     * girdiği için, konsolu buraya eklemek üretimde veritabanı konsolunu
     * herkese açık bırakırdı. Konsol yalnızca dev profilinde etkin
     * ({@code application-dev.properties}).
     *
     * @param http Spring'in sağladığı güvenlik yapılandırma nesnesi
     * @return üretimde kullanılan korumalı filtre zinciri
     * @throws Exception yapılandırma sırasında oluşan hata
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
                        /* Kullanıcı kaydı herkese açık değil; sadece ADMIN ekleyebilir. */
                        .requestMatchers("/api/auth/register").hasRole("ADMIN")
                        /* AI'a yeniden özetletme, sadece ADMIN yetkisiyle tetiklenebilir. */
                        .requestMatchers("/api/tickets/*/resummarize").hasRole("ADMIN")
                        /*
                         * Park kuyruğunu görüntülemek ve geri oynatmak operasyonel bir
                         * işlem: kuyruk durumunu sızdırmamak ve toplu yeniden analizi
                         * herkesin tetikleyememesi için ADMIN'e kısıtlı.
                         */
                        .requestMatchers("/api/tickets/analysis/parked/**").hasRole("ADMIN")
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
