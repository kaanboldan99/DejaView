package com.skaanb.DejaView.service;

import com.skaanb.DejaView.exception.AiSummarizationException;

// Dependency Inversion: TicketAnalysisListener somut bir AI sağlayıcısına (Gemini, OpenAI...)
// değil, bu arayüze bağımlı. Yeni bir sağlayıcı eklemek istediğinde (Open/Closed) sadece
// bu arayüzü implemente eden yeni bir @Service yazman yeterli, listener'a dokunmana gerek yok.
public interface AiSummarizationService {

    // Başarılıysa özet/çözüm metnini döner; başarısızsa (API hatası, timeout, beklenmeyen
    // yanıt şekli...) AiSummarizationException fırlatır — sessizce "başarısız" bir string
    // dönmez, böylece çağıran taraf başarı/başarısızlığı string karşılaştırmasıyla değil
    // gerçek bir exception ile ayırt eder.
    String summarize(String description);
}
