package com.skaanb.DejaView.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("DejaView API Dokümantasyonu")
                        .version("1.0.0")
                        .description("DejaView uygulaması hata takip, yapay zeka analiz ve Elasticsearch arama motoru servisleri API sözleşmesi."))
                // Swagger UI arayüzüne global olarak JWT 'Bearer' token giriş alanı ekliyoruz
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