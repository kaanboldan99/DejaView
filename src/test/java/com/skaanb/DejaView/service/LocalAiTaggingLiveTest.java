package com.skaanb.DejaView.service;

import com.skaanb.DejaView.dto.AIAnalysisResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Bu makinede çalışan yerel modele (LM Studio) karşı gerçek istek atan opsiyonel test.
// GeminiServiceLiveTest ile aynı mantık: CI'da ve normal "mvn test" koşusunda ortam
// değişkeni set olmadığı için otomatik atlanır.
//
// Çalıştırmadan önce sunucunun ayakta olması gerekiyor:
//   lms server start
//   lms load qwen/qwen3-8b --context-length 4096 --parallel 1 --gpu max
// Sonra:
//   LOCAL_AI=1 mvn test -Dtest=LocalAiTaggingLiveTest
//
// @SpringBootTest KULLANILMIYOR (GeminiServiceLiveTest'in aksine): burada test edilen şey
// yalnızca OpenAiService'in HTTP davranışı ve yanıt ayrıştırması. Spring context'i açmak
// Elasticsearch ve RabbitMQ bağlantısı da gerektirirdi; yerel modeli denemek için o iki
// servisin ayakta olmasını şart koşmanın anlamı yok.
@EnabledIfEnvironmentVariable(named = "LOCAL_AI", matches = ".+")
class LocalAiTaggingLiveTest {

    // Yerel model bulut API'lerinden yavaş; timeout'u bolca veriyoruz (bkz.
    // application-local-ai.properties'teki openai.api.timeout-seconds).
    private final OpenAiService service = new OpenAiService(
            "",
            "http://localhost:1234/v1/chat/completions",
            System.getenv().getOrDefault("LOCAL_AI_MODEL", "qwen/qwen3-8b"),
            180,
            5);

    @Test
    void testAnalyze_YerelModeldenSemayaUygunEtiketVeOzetDoner() {
        AIAnalysisResponse analysis = service.analyze(
                "PostgreSQL sunucusuna bağlanılamadı, port 5432 connection refused. "
                        + "Servis 3 dakikadır yanıt vermiyor, connection pool doldu.");

        assertNotNull(analysis.getDescription());
        assertFalse(analysis.getDescription().isBlank());
        assertNotNull(analysis.getSolution());
        assertFalse(analysis.getSolution().isBlank());

        List<String> tags = analysis.getTags();
        assertFalse(tags.isEmpty(), "Yapısal çıktı kullanıldığı için etiket listesi boş gelmemeli");
        assertTrue(tags.size() <= 5, "max-tags sınırı aşılmamalı, gelen: " + tags);

        // Etiketlerin İÇERİĞİ üzerine assertion yok: model çıktısı deterministik değil ve
        // testi belirli kelimelere bağlamak onu kırılgan yapar. Doğrulanan şey sözleşme —
        // şemaya uygun, normalize edilmiş, sınırı aşmayan bir liste geldiği.
        for (String tag : tags) {
            assertFalse(tag.isBlank(), "Boş etiket olmamalı");
            assertEquals(tag.toLowerCase(java.util.Locale.ROOT), tag, "Etiketler küçük harfe normalize edilmeli");
            assertEquals(tag.trim(), tag, "Etiketlerde baştaki/sondaki boşluk temizlenmeli");
        }

        System.out.println("Yerel model etiketleri: " + tags);
        System.out.println("Özet: " + analysis.getDescription());
    }
}
