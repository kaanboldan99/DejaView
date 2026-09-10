package com.skaanb.DejaView.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Her istekte {@code Authorization} başlığındaki JWT'yi doğrulayıp kullanıcıyı tanıyan filtre.
 *
 * Nasıl çalışır: {@link OncePerRequestFilter}'dan türediği için istek başına
 * TEK kez çalışır (yönlendirme/forward durumlarında tekrar etmez). Zincire
 * {@code UsernamePasswordAuthenticationFilter}'dan ÖNCE eklenir
 * (bkz. {@link com.skaanb.DejaView.config.SecurityConfig}), yani yetkilendirme
 * kararları alınmadan önce kimlik yerine oturmuş olur.
 *
 * Filtre hiçbir isteği kendisi REDDETMEZ: token yoksa ya da geçersizse isteği
 * doğrulanmamış olarak zincire bırakır ve kabul/ret kararını yetkilendirme
 * katmanına devreder. Bu ayrım sayesinde açık uçlar (giriş, Swagger) aynı
 * filtreden sorunsuz geçer.
 *
 * Oturum durumu tutulmaz; kimlik her istekte token'dan yeniden kurulur.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** Token'ı çözen ve doğrulayan yardımcı. */
    private final JwtUtil jwtUtil;

    /** Kullanıcıyı ve yetkilerini veritabanından yükleyen servis. */
    private final UserDetailsService userDetailsService;

    /**
     * @param jwtUtil            token çözme/doğrulama yardımcısı
     * @param userDetailsService kullanıcı ve yetki bilgisini yükleyen servis
     */
    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    /**
     * İsteği inceler, geçerli bir JWT varsa güvenlik bağlamına kimliği yerleştirir.
     *
     * Nasıl çalışır: sırasıyla (1) {@code Authorization: Bearer ...} başlığı
     * aranır — yoksa istek olduğu gibi devam eder; (2) token'dan kullanıcı adı
     * çıkarılır; (3) bağlamda zaten bir kimlik yoksa kullanıcı yüklenir ve
     * token onunla doğrulanır; (4) doğrulama geçerse kimlik güvenlik bağlamına
     * yazılır. Her durumda zincir çağrılır.
     *
     * Bağlamda kimlik varsa yeniden yüklenmemesinin sebebi: aynı istekte daha
     * önce kimlik doğrulanmışsa onu ezmemek.
     *
     * @param request     gelen HTTP isteği; token başlığı buradan okunur
     * @param response    HTTP yanıtı; bu filtre içeriğine dokunmaz
     * @param filterChain zincirin devamı; her yolda mutlaka çağrılır
     * @throws ServletException zincirin devamında oluşan servlet hatası
     * @throws IOException      zincirin devamında oluşan giriş/çıkış hatası
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        /* "Bearer " ön ekinden sonrası token'ın kendisi. */
        String token = authHeader.substring(7);

        String username = jwtUtil.extractUsername(token);

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (UsernameNotFoundException e) {
                /*
                 * Token geçerli imzalı ama kullanıcı artık yok (silinmiş hesap ya da
                 * bellek içi H2 yeniden başlamış olabilir). Bu bir sunucu hatası değil:
                 * istek doğrulanmamış olarak devam eder, korumalı ortamda 401 döner ve
                 * istemci oturumu kapatabilir. Aksi halde burada 500 fırlardı.
                 */
                logger.warn("JWT geçerli fakat kullanıcı bulunamadı. username={}", username);
            }
        }

        filterChain.doFilter(request, response);
    }
}
