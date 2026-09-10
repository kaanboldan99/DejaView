package com.skaanb.DejaView.exception;

/**
 * AI sağlayıcısı bir analiz üretemediğinde fırlatılan unchecked exception.
 *
 * Nasıl çalışır: {@link com.skaanb.DejaView.service.AiSummarizationService}
 * implementasyonları (Gemini, OpenAI uyumlu sağlayıcılar) hata durumunda
 * bu tipi fırlatır; {@link com.skaanb.DejaView.service.TicketAnalysisListener}
 * bunu yakalayıp mesajı silmek yerine bekletme ya da park kuyruğuna yönlendirir.
 *
 * Önceden başarısızlık sabit bir "başarısız" metni döndürülerek bildiriliyordu
 * ve çağıran taraf bunu string karşılaştırmasıyla anlamak zorundaydı; gerçek
 * bir exception, başarısızlığı yanlışlıkla geçerli bir analiz sanma ihtimalini
 * ortadan kaldırıyor.
 */
public class AiSummarizationException extends RuntimeException {

    /**
     * @param message hatanın insan tarafından okunabilir açıklaması; park
     *                kuyruğunda {@code x-park-reason} başlığı olarak da taşınır
     * @param cause   asıl hata (HTTP hatası, zaman aşımı, ayrıştırma hatası).
     *                Korunması önemli: çağıran taraf 4xx ile zaman aşımını
     *                bu sayede ayırt ediyor
     *                (bkz. {@link com.skaanb.DejaView.service.OpenAiService})
     */
    public AiSummarizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
