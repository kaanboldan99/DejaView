package com.skaanb.DejaView.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI dokümantasyonunun yapılandırması.
 *
 * Nasıl çalışır: springdoc, uçları tarayıp OpenAPI şemasını kendisi üretir;
 * bu sınıf yalnızca üretilen şemaya başlık, sürüm ve JWT güvenlik tanımını
 * ekler. Arayüz adresleri {@code application.properties} içindeki
 * {@code springdoc.*} ayarlarından gelir.
 *
 * Not: Swagger uçları yalnızca korumalı zincirde açıkça {@code permitAll}
 * yapıldığı için erişilebilir (bkz. {@link SecurityConfig}).
 */
@Configuration
public class SwaggerConfig {

    /**
     * OpenAPI belgesinin üst bilgilerini ve JWT güvenlik şemasını tanımlar.
     *
     * Nasıl çalışır: güvenlik şeması hem {@code components} altına eklenir
     * (tanım) hem de {@code addSecurityItem} ile GLOBAL gereksinim yapılır —
     * ikincisi olmadan Swagger arayüzünde token giriş alanı çıkar ama istekler
     * token'sız gönderilirdi.
     *
     * @return springdoc'un ürettiği şemaya uygulanacak OpenAPI yapılandırması
     */
    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("DejaView API Dokümantasyonu")
                        .version("1.0.0")
                        .description("DejaView uygulaması hata takip, yapay zeka analiz ve Elasticsearch arama motoru servisleri API sözleşmesi."))
                /* Arayüze global olarak JWT 'Bearer' token giriş alanı ekler. */
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Lütfen 'Bearer ' takısı olmadan sadece JWT token değerinizi giriniz.")));
    }
}
