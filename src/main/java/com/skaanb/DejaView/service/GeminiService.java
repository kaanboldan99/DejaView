package com.skaanb.DejaView.service;

import com.skaanb.DejaView.exception.AiSummarizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.List;

// Aktif sağlayıcı dejaview.ai.provider ile seçiliyor; varsayılan (matchIfMissing) Gemini,
// yani ayar verilmediğinde davranış eskisi gibi. Önceden bu seçim @Primary ile yapılıyordu
// ama @Primary sağlayıcıyı KODA gömüyordu: yerel bir modele geçmek için sınıf düzenleyip
// yeniden derlemek gerekiyordu. Artık profil/ortam değişkeni yetiyor
// (bkz. application-local-ai.properties) ve aynı anda tek bir bean ayakta olduğu için
// @Primary'ye de gerek kalmıyor.
@Service
@ConditionalOnProperty(name = "dejaview.ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiService implements AiSummarizationService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    // Model adı sabit kodlanmıyor; application.properties'teki gemini.api.url'den okunuyor.
    // Önceden burada sabit "gemini-pro" kullanılıyordu — bu model Google tarafından
    // kaldırıldığı için her çağrı 404 ile başarısız oluyordu (canlı API'ye karşı test
    // edilerek doğrulandı). Model adı zaman içinde değiştiği için konfigürasyondan
    // okunması, kod değişikliği gerektirmeden güncellenebilmesini sağlıyor.
    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String summarize(String description) {
        try {
            String url = apiUrl + "?key=" + apiKey;

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
