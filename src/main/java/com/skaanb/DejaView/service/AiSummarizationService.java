package com.skaanb.DejaView.service;

import com.skaanb.DejaView.dto.AIAnalysisResponse;
import com.skaanb.DejaView.exception.AiSummarizationException;

import java.util.List;

// Dependency Inversion: TicketAnalysisListener somut bir AI sağlayıcısına (Gemini, OpenAI...)
// değil, bu arayüze bağımlı. Yeni bir sağlayıcı eklemek istediğinde (Open/Closed) sadece
// bu arayüzü implemente eden yeni bir @Service yazman yeterli, listener'a dokunmana gerek yok.
public interface AiSummarizationService {

    // Başarılıysa özet/çözüm metnini döner; başarısızsa (API hatası, timeout, beklenmeyen
    // yanıt şekli...) AiSummarizationException fırlatır — sessizce "başarısız" bir string
    // dönmez, böylece çağıran taraf başarı/başarısızlığı string karşılaştırmasıyla değil
    // gerçek bir exception ile ayırt eder.
    String summarize(String description);

    // Özet + etiket + çözümü TEK çağrıda ve yapısal olarak döner. Etiketleme için ayrı bir
    // istek atmak yerine bunun tek çağrı olmasının sebebi: model zaten açıklamayı okuyor,
    // etiketleri de aynı geçişte üretmesi hem yarı yarıya ucuz hem de özet ile etiketlerin
    // birbiriyle tutarlı olmasını garantiliyor.
    //
    // Varsayılan implementasyon yapısal çıktı DESTEKLEMEYEN sağlayıcılar için bir köprü:
    // summarize()'a düşer ve etiket üretmez (boş liste). Yani Gemini gibi bu metodu
    // override etmeyen bir sağlayıcı aktifken sistem çalışmaya devam eder, sadece
    // AI etiketi gelmez — çağıran tarafın boş listeyi tolere etmesi gerekir
    // (bkz. TicketAnalysisListener). Gerçek etiketleme için sağlayıcının bunu
    // override etmesi lazım (bkz. OpenAiService).
    default AIAnalysisResponse analyze(String description) {
        String summary = summarize(description);

        AIAnalysisResponse response = new AIAnalysisResponse();
        response.setDescription(summary);
        response.setSolution(summary);
        response.setTags(List.of());
        return response;
    }
}
