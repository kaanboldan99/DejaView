package com.skaanb.DejaView.service;

import com.skaanb.DejaView.dto.AIAnalysisResponse;
import com.skaanb.DejaView.exception.AiSummarizationException;

import java.util.List;

/**
 * AI sağlayıcılarının uyduğu sözleşme.
 *
 * Nasıl çalışır: Bağımlılığın Tersine Çevrilmesi (Dependency Inversion) —
 * {@link TicketAnalysisListener} somut bir sağlayıcıya (Gemini, OpenAI...)
 * değil bu arayüze bağımlıdır. Yeni bir sağlayıcı eklemek için (Açık/Kapalı
 * ilkesi) yalnızca bu arayüzü uygulayan yeni bir {@code @Service} yazmak
 * yeterlidir; dinleyiciye dokunmak gerekmez.
 *
 * Hangi sağlayıcının aktif olacağı {@code dejaview.ai.provider} ayarıyla
 * seçilir; aynı anda tek bir uygulama bean'i ayakta olur.
 *
 * Arayüz iki seviyeli: tek zorunlu metot {@link #summarize(String)}, geri
 * kalanlar varsayılan gövdeleriyle onun üzerine kurulur. Böylece yapısal çıktı
 * desteklemeyen basit bir sağlayıcı bile sisteme takılabilir.
 */
public interface AiSummarizationService {

    /**
     * Verilen açıklamayı özetler.
     *
     * Nasıl çalışır: başarılıysa özet/çözüm metnini döner; başarısızsa (API
     * hatası, zaman aşımı, beklenmeyen yanıt biçimi) sessizce "başarısız" bir
     * metin DÖNMEZ, {@link AiSummarizationException} fırlatır. Böylece çağıran
     * taraf başarı/başarısızlığı metin karşılaştırmasıyla değil gerçek bir
     * hatayla ayırt eder.
     *
     * @param description özetlenecek hata açıklaması
     * @return modelin ürettiği özet metni
     * @throws AiSummarizationException sağlayıcıdan geçerli bir yanıt alınamazsa
     */
    String summarize(String description);

    /**
     * Açıklama, kök neden, çözümler ve etiketleri TEK çağrıda üretir.
     *
     * Nasıl çalışır: etiketleme için ayrı bir istek atmak yerine tek çağrı
     * olmasının sebebi şu — model zaten açıklamayı okuyor; etiketleri de aynı
     * geçişte üretmesi hem yarı yarıya ucuz hem de özet ile etiketlerin
     * birbiriyle tutarlı olmasını garantiliyor.
     *
     * Buradaki varsayılan gövde, yapısal çıktı DESTEKLEMEYEN sağlayıcılar için
     * bir köprüdür: {@link #summarize(String)} çağrılır, kök neden boş bırakılır
     * ve etiket üretilmez. Yani bu metodu geçersiz kılmayan bir sağlayıcı
     * aktifken sistem çalışmaya devam eder, sadece AI etiketi gelmez — çağıran
     * tarafın boş listeyi tolere etmesi gerekir (bkz.
     * {@link TicketAnalysisListener}). Gerçek etiketleme için sağlayıcının bu
     * metodu geçersiz kılması gerekir (bkz. {@link OpenAiService}).
     *
     * @param description analiz edilecek hata açıklaması
     * @return dolu analiz nesnesi; etiket listesi boş olabilir
     * @throws AiSummarizationException sağlayıcıdan geçerli bir yanıt alınamazsa
     */
    default AIAnalysisResponse analyze(String description) {
        String summary = summarize(description);

        AIAnalysisResponse response = new AIAnalysisResponse();
        response.setDescription(summary);
        response.setRootCause(null);
        response.setSolutions(List.of(summary));
        response.setTags(List.of());
        return response;
    }

    /**
     * Aynı analizi, "kullanıcı bunu bilerek yeniden istedi" bilgisiyle yapar.
     *
     * Nasıl çalışır: sağlayıcı bu bilgiyi aynı çıktıyı tekrarlamamak için
     * kullanabilir (bkz. {@link OpenAiService}: sıcaklık yükselir ve prompt
     * farklı bir bakış açısı ister). Bilgiyi kullanmayan sağlayıcılar için
     * buradaki varsayılan gövde normal analize düşer — yani yeniden üretimi
     * desteklemek zorunlu değildir.
     *
     * @param description analiz edilecek hata açıklaması
     * @param regenerate  {@code true} ise kullanıcının açık "yeniden üret" isteği
     * @return dolu analiz nesnesi
     * @throws AiSummarizationException sağlayıcıdan geçerli bir yanıt alınamazsa
     */
    default AIAnalysisResponse analyze(String description, boolean regenerate) {
        return analyze(description);
    }
}
