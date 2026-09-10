package com.skaanb.DejaView.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaanb.DejaView.dto.AIAnalysisResponse;
import com.skaanb.DejaView.exception.AiSummarizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

// OpenAI'ın /v1/chat/completions protokolünü konuşan sağlayıcı. Adres ve model adı
// konfigürasyondan geldiği için bu sınıf AYNI ZAMANDA yerel modeller için de kullanılıyor:
// LM Studio (ve Ollama, vLLM, llama.cpp server) tam olarak bu protokolü sunuyor, dolayısıyla
// openai.api.url'i http://localhost:1234/v1/chat/completions yapmak yerel bir modele
// bağlanmak için yeterli — ayrı bir LocalAiService yazmaya gerek yok.
// Yerel kurulum için bkz. application-local-ai.properties.
//
// Hangi sağlayıcının aktif olacağı dejaview.ai.provider ile seçiliyor; bu sayede aynı anda
// yalnızca tek bir AiSummarizationService bean'i var ve @Primary'ye ihtiyaç kalmıyor
// (eskiden GeminiService @Primary idi — bkz. GeminiService).
@Service
@ConditionalOnProperty(name = "dejaview.ai.provider", havingValue = "openai")
public class OpenAiService implements AiSummarizationService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final int maxTags;

    public OpenAiService(@Value("${openai.api.key:}") String apiKey,
                         @Value("${openai.api.url}") String apiUrl,
                         @Value("${openai.api.model}") String model,
                         @Value("${openai.api.timeout-seconds:60}") int timeoutSeconds,
                         @Value("${dejaview.ai.max-tags:5}") int maxTags) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.maxTags = maxTags;

        // Timeout AÇIKÇA veriliyor: RestTemplate'in varsayılanı "sonsuz bekle"dir ve yerel
        // model kullanırken bu gerçek bir risk — makine takılırsa RabbitMQ listener thread'i
        // süresiz bloke olur ve kuyruk birikir. Yerel modeller bulut API'lerinden belirgin
        // şekilde yavaş olduğu için süre konfigürasyondan okunuyor (bkz.
        // application-local-ai.properties).
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restTemplate = new RestTemplate(requestFactory);

        logger.info("OpenAI uyumlu sağlayıcı hazır. url={}, model={}, timeout={}s", apiUrl, model, timeoutSeconds);
    }

    @Override
    public String summarize(String text) {
        return chat(
                List.of(
                        Map.of("role", "system", "content", "Aşağıdaki metni kısa bir şekilde özetle."),
                        Map.of("role", "user", "content", text)
                ),
                null
        );
    }

    // Özet + etiket + çözümü tek çağrıda üretir. Modelden "lütfen JSON dön" diye RİCA
    // etmiyoruz; response_format ile bir JSON şeması dayatıyoruz. Fark önemli: şema
    // verildiğinde sunucu decode aşamasında sadece şemaya uyan token'lara izin veriyor,
    // yani "JSON'un etrafına açıklama yazdı" ya da "tags'i string döndürdü" gibi klasik
    // parse hataları yapısal olarak imkânsız hale geliyor. Yerel 8B sınıfı modellerde
    // etiketlemeyi güvenilir kılan asıl şey bu — model boyutundan daha belirleyici.
    @Override
    public AIAnalysisResponse analyze(String description) {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "description", Map.of("type", "string"),
                        "tags", Map.of("type", "array", "items", Map.of("type", "string")),
                        "solution", Map.of("type", "string")
                ),
                "required", List.of("description", "tags", "solution"),
                "additionalProperties", false
        );

        Map<String, Object> responseFormat = Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "ticket_analysis",
                        "strict", true,
                        "schema", schema
                )
        );

        String systemPrompt = "Sen bir hata takip sistemi asistanısın. Verilen hata kaydını analiz et ve "
                + "Türkçe yanıtla. description alanına hatanın bir cümlelik özetini, solution alanına "
                + "olası çözüm adımlarını yaz. Etiketler kısa, tekil, küçük harfli teknik terimler olsun "
                + "(ör. 'database', 'timeout', 'auth'); port numarası veya sürüm gibi kayda özel "
                + "değerleri etiket yapma. En fazla " + maxTags + " etiket üret.";

        String content = chat(
                List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", description)
                ),
                responseFormat
        );

        try {
            AIAnalysisResponse analysis = objectMapper.readValue(content, AIAnalysisResponse.class);
            analysis.setTags(normalizeTags(analysis.getTags()));
            return analysis;
        } catch (Exception e) {
            logger.error("AI yanıtı JSON olarak ayrıştırılamadı. model={}, yanıt={}", model, content, e);
            throw new AiSummarizationException("AI analiz yanıtı ayrıştırılamadı.", e);
        }
    }

    // Etiketler doğrudan modelden geldiği için normalize ediliyor: Elasticsearch tarafında
    // aiTags Keyword tipinde (bkz. TicketDocument), yani "Database" ile "database" AYRI iki
    // etiket olarak indekslenir ve filtreleme bozulur. Küçük harfe çevirip tekrarları
    // eleyerek bunu kaynağında engelliyoruz.
    private List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : rawTags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            // Locale.ROOT bilinçli: etiketler İngilizce teknik terimler ve Türkçe locale ile
            // küçültmek bunları bozar ("TIMEOUT" -> "tımeout", noktasız ı). Kayıtların dili
            // Türkçe olsa da etiket alfabesi değil.
            normalized.add(tag.trim().toLowerCase(Locale.ROOT));
            if (normalized.size() == maxTags) {
                break;
            }
        }
        return new ArrayList<>(normalized);
    }

    @SuppressWarnings("unchecked")
    private String chat(List<Map<String, String>> messages, Map<String, Object> responseFormat) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Yerel sunucular (LM Studio, Ollama) anahtar istemiyor; boşken Authorization
        // başlığını hiç göndermiyoruz ki "Bearer " gibi anlamsız bir değer gitmesin.
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        // Etiketleme bir sınıflandırma işi: aynı hataya her seferinde aynı etiketlerin
        // gelmesi yaratıcılıktan daha değerli, o yüzden sıcaklık düşük.
        body.put("temperature", 0.2);
        if (responseFormat != null) {
            body.put("response_format", responseFormat);
        }

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    apiUrl, new HttpEntity<>(body, headers), Map.class);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            if (content == null || content.isBlank()) {
                throw new AiSummarizationException("AI sağlayıcısı boş yanıt döndü.", null);
            }
            return content;

        } catch (AiSummarizationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("AI çağrısı başarısız oldu. url={}, model={}, hata={}", apiUrl, model, e.getMessage(), e);
            throw new AiSummarizationException("AI yanıtı alınamadı.", e);
        }
    }
}
