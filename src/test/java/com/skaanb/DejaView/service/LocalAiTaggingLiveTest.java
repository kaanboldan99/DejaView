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

    // Sınırlar application.properties'teki dejaview.ai.* değerleriyle aynı; test
    // konfigürasyonu okumadığı (Spring context'i açılmıyor) için burada tekrarlanıyor.
    private static final int MIN_TAGS = 10;
    private static final int MAX_TAGS = 14;
    private static final int MIN_SOLUTIONS = 3;
    private static final int MAX_SOLUTIONS = 5;

    // Yerel model bulut API'lerinden yavaş; timeout'u bolca veriyoruz (bkz.
    // application-local-ai.properties'teki openai.api.timeout-seconds).
    private final OpenAiService service = new OpenAiService(
            "",
            "http://localhost:1234/v1/chat/completions",
            System.getenv().getOrDefault("LOCAL_AI_MODEL", "qwen/qwen3-8b"),
            180,
            MIN_TAGS,
            MAX_TAGS,
            MIN_SOLUTIONS,
            MAX_SOLUTIONS);

    @Test
    void testAnalyze_YerelModeldenSemayaUygunEtiketVeOzetDoner() {
        AIAnalysisResponse analysis = service.analyze(
                "PostgreSQL sunucusuna bağlanılamadı, port 5432 connection refused. "
                        + "Servis 3 dakikadır yanıt vermiyor, connection pool doldu.");

        assertNotNull(analysis.getDescription());
        assertFalse(analysis.getDescription().isBlank());
        assertNotNull(analysis.getRootCause());
        assertFalse(analysis.getRootCause().isBlank(), "Kök neden alanı doldurulmalı");

        List<String> solutions = analysis.getSolutions();
        assertTrue(solutions.size() >= MIN_SOLUTIONS,
                "En az " + MIN_SOLUTIONS + " çözüm önerisi beklenir, gelen: " + solutions);
        assertTrue(solutions.size() <= MAX_SOLUTIONS,
                "max-solutions sınırı aşılmamalı, gelen: " + solutions);
        for (String solution : solutions) {
            assertFalse(solution.isBlank(), "Boş çözüm önerisi olmamalı");
        }

        List<String> tags = analysis.getTags();
        // Alt sınır sözleşmenin kendisi: şemadaki minItems tutmazsa OpenAiService eksik
        // kalan etiketler için ikinci bir çağrı yapıyor (bkz. topUpTags), yani buraya
        // gelen listenin hedefi tutturmuş olması bekleniyor.
        assertTrue(tags.size() >= MIN_TAGS, "En az " + MIN_TAGS + " etiket beklenir, gelen: " + tags);
        assertTrue(tags.size() <= MAX_TAGS, "max-tags sınırı aşılmamalı, gelen: " + tags);

        // Etiketlerin İÇERİĞİ üzerine assertion yok: model çıktısı deterministik değil ve
        // testi belirli kelimelere bağlamak onu kırılgan yapar. Doğrulanan şey sözleşme —
        // şemaya uygun, normalize edilmiş, sınırı aşmayan bir liste geldiği.
        for (String tag : tags) {
            assertFalse(tag.isBlank(), "Boş etiket olmamalı");
            assertEquals(tag.toLowerCase(java.util.Locale.ROOT), tag, "Etiketler küçük harfe normalize edilmeli");
            assertEquals(tag.trim(), tag, "Etiketlerde baştaki/sondaki boşluk temizlenmeli");
        }

        System.out.println("Yerel model etiketleri (" + tags.size() + "): " + tags);
        System.out.println("Özet: " + analysis.getDescription());
        System.out.println("Kök neden: " + analysis.getRootCause());
        System.out.println("Çözümler: " + solutions);
    }
}
