package com.skaanb.DejaView.service;

import com.skaanb.DejaView.exception.AiSummarizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.List;

// @Primary: AiSummarizationService'in birden fazla implementasyonu olduğunda (bkz. OpenAiService)
// Spring'in qualifier belirtilmeden hangisini enjekte edeceğini bilmesi için aktif sağlayıcı.
@Service
@Primary
public class GeminiService implements AiSummarizationService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
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
            logger.error("Gemini API çağrısı başarısız oldu: {}", e.getMessage(), e);
            throw new AiSummarizationException("Gemini ile özet oluşturulamadı.", e);
        }
    }
}
