package com.skaanb.DejaView.exception;

// AiSummarizationService implementasyonlarının (Gemini, OpenAI...) özet üretemediğinde
// fırlattığı unchecked exception. Önceden GeminiService sabit bir "başarısız" metni
// döndürüyordu ve çağıran taraf bunu string karşılaştırmasıyla anlamak zorundaydı;
// artık başarısızlık gerçek bir exception ile sinyalleniyor.
public class AiSummarizationException extends RuntimeException {

    public AiSummarizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
