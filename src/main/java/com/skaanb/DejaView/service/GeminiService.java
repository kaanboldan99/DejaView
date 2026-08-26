package com.skaanb.DejaView.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.List;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public String summarize(String description) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + apiKey;

            // Gemini API'nin beklediği JSON veri yapısı
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", "Lütfen şu bilet açıklamasını kısa bir cümleyle özetle: " + description)
                            ))
                    )
            );

            // API'ye istek atma
            Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);

            // Gelen JSON yanıtından metni filtreleme (Güvenli veri ayıklama)
            List<?> candidates = (List<?>) response.get("candidates");
            Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
            Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
            List<?> parts = (List<?>) content.get("parts");
            Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);

            return firstPart.get("text").toString().trim();

        } catch (Exception e) {
            // Loglama yapabilir veya hata durumunda fallback dönebilirsin
            return "Gemini ile özet oluşturulamadı.";
        }
    }
}