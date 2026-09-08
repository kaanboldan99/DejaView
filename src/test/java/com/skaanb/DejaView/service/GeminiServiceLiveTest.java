package com.skaanb.DejaView.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

// Gerçek Gemini API'sine karşı çalışan opsiyonel canlı doğrulama testi.
// Normal test suite'te (CI dahil) GEMINI_API_KEY ortam değişkeni set olmadığı
// için otomatik atlanır — mock'larla yapılan diğer testlerin aksine, bu test
// gerçekten ağ üzerinden Google'a istek atıp gerçek bir yanıt bekler. Sadece
// yerelde elle doğrulamak için:
//   GEMINI_API_KEY=xxx mvn test -Dtest=GeminiServiceLiveTest
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiServiceLiveTest {

    @Autowired
    private GeminiService geminiService;

    @Test
    void testSummarize_GercekGeminiApisineIstekAtarVeOzetDoner() {
        String summary = geminiService.summarize(
                "PostgreSQL sunucusuna bağlanılamadı, port 5432 refused.");

        assertNotNull(summary);
        assertFalse(summary.isBlank());
        // GeminiService.SUMMARY_FAILED_MESSAGE sabiti kaldırıldı; başarısızlık artık
        // exception ile sinyalleniyor, bu satıra ulaşıldıysa gerçek bir özet gelmiş demektir.
    }
}
