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

/**
 * Google Gemini API'sini konuşan AI sağlayıcısı.
 *
 * Nasıl çalışır: aktif sağlayıcı {@code dejaview.ai.provider} ayarıyla seçilir;
 * {@code matchIfMissing} sayesinde ayar hiç verilmediğinde bu bean oluşur, yani
 * varsayılan davranış eskisi gibi kalır.
 *
 * Önceden bu seçim {@code @Primary} ile yapılıyordu, ama {@code @Primary}
 * sağlayıcıyı KODA gömüyordu: yerel bir modele geçmek için sınıfı düzenleyip
 * yeniden derlemek gerekiyordu. Artık profil/ortam değişkeni yetiyor ve aynı
 * anda tek bir bean ayakta olduğu için {@code @Primary}'ye de gerek kalmıyor.
 *
 * Bu sağlayıcı yalnızca {@link #summarize(String)} metodunu uygular; yapısal
 * çıktıyı (kök neden, çoklu çözüm, etiketler) desteklemez, o yüzden aktifken
 * arayüzün varsayılan {@code analyze} gövdeleri devreye girer ve AI etiketi
 * üretilmez.
 */
@Service
@ConditionalOnProperty(name = "dejaview.ai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiService implements AiSummarizationService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    /** Gemini API anahtarı; adresin sorgu parametresi olarak gönderilir. */
    @Value("${gemini.api.key}")
    private String apiKey;

    /**
     * Model uç adresi; model adını İÇERİR.
     *
     * Nasıl çalışır: model adı sabit kodlanmaz, {@code gemini.api.url}
     * ayarından okunur. Önceden burada sabit {@code gemini-pro} kullanılıyordu;
     * bu model Google tarafından kaldırıldığı için her çağrı 404 ile başarısız
     * oluyordu (canlı API'ye karşı test edilerek doğrulandı). Model adları
     * zamanla değiştiği için ayardan okunması, kod değişikliği gerektirmeden
     * güncellenebilmesini sağlıyor.
     */
    @Value("${gemini.api.url}")
    private String apiUrl;

    /** HTTP istemcisi; zaman aşımı ayarı verilmemiştir. */
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Açıklamayı Gemini'ye özetletir.
     *
     * Nasıl çalışır: anahtar sorgu parametresi olarak eklenir, istek gövdesi
     * Gemini'nin beklediği iç içe {@code contents/parts/text} yapısıyla kurulur
     * ve yanıttan metin, {@code candidates -> content -> parts -> text} yolu
     * izlenerek çıkarılır.
     *
     * Yanıt ayrıştırması dinamik {@code Map} üzerinden yapıldığı için beklenen
     * yapı gelmediğinde tip dönüşümü hatası oluşur; bu yüzden ağ hatası da
     * biçim hatası da tek bir {@code catch} ile ele alınıp
     * {@link AiSummarizationException}'a çevrilir — çağıran taraf ikisini de
     * "AI şu an üretemedi" olarak görmelidir.
     *
     * @param description özetlenecek hata açıklaması
     * @return modelin ürettiği, baştaki/sondaki boşlukları kırpılmış özet
     * @throws AiSummarizationException çağrı başarısızsa veya yanıt beklenen
     *                                  biçimde değilse
     */
    @Override
    public String summarize(String description) {
        try {
            String url = apiUrl + "?key=" + apiKey;

            /* Gemini API'nin beklediği iç içe JSON yapısı. */
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", "Lütfen şu bilet açıklamasını kısa bir cümleyle özetle: " + description)
                            ))
                    )
            );

            Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);

            /* Yanıt ağacından metni adım adım çıkarma. */
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
