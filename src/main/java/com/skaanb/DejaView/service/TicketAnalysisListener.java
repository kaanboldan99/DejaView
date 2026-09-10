package com.skaanb.DejaView.service;

import com.skaanb.DejaView.config.RabbitConfig;
import com.skaanb.DejaView.dto.AIAnalysisResponse;
import com.skaanb.DejaView.dto.TicketAnalysisMessage;
import com.skaanb.DejaView.exception.AiSummarizationException;
import com.skaanb.DejaView.model.TicketStatus;
import com.skaanb.DejaView.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * Analiz kuyruğunu tüketip AI sonucunu kayda işleyen dinleyici.
 *
 * Nasıl çalışır: kuyruktan gelen her mesaj için sırasıyla — kayıt hâlâ var mı
 * diye bakılır, durum PROCESSING yapılır, {@link AiSummarizationService}'ten
 * analiz istenir ve sonuç dokümana yazılıp durum COMPLETED yapılır.
 *
 * Sağlayıcıya değil arayüze bağımlıdır (Dependency Inversion), yani Gemini mi
 * yerel bir model mi çalıştığını bilmez.
 *
 * {@code spring.rabbitmq.listener.simple.concurrency} ayarı sayesinde birden
 * fazla thread bu metodu aynı anda çalıştırabilir; aynı kayda yapılan eşzamanlı
 * yazmalar {@link TicketMutationExecutor} üzerinden iyimser kilitleme ve
 * yeniden deneme ile güvenli biçimde birleştirilir.
 *
 * AI hatası bu isteğin ÖLÜMÜ DEĞİLDİR: mesaj kuyruktan düşürülmez, bekletilip
 * yeniden denenir, deneme hakkı biterse park edilir.
 */
@Component
public class TicketAnalysisListener {

    private static final Logger logger = LoggerFactory.getLogger(TicketAnalysisListener.class);

    /** Kaydın hâlâ var olup olmadığını kontrol etmek için depo. */
    private final TicketRepository ticketRepository;

    /** Aktif AI sağlayıcısı; somut sınıfı bilinmez. */
    private final AiSummarizationService aiSummarizationService;

    /** Kayıt güncellemelerini çakışmaya karşı güvenli yürüten bileşen. */
    private final TicketMutationExecutor ticketMutationExecutor;

    /** Yeniden deneme ve park işlemlerini kuyruğa yazan üretici. */
    private final TicketAnalysisProducer ticketAnalysisProducer;

    /**
     * @param ticketRepository       kayıt deposu
     * @param aiSummarizationService aktif AI sağlayıcısı
     * @param ticketMutationExecutor çakışma güvenli güncelleme bileşeni
     * @param ticketAnalysisProducer yeniden deneme/park üreticisi
     */
    public TicketAnalysisListener(TicketRepository ticketRepository,
                                   AiSummarizationService aiSummarizationService,
                                   TicketMutationExecutor ticketMutationExecutor,
                                   TicketAnalysisProducer ticketAnalysisProducer) {
        this.ticketRepository = ticketRepository;
        this.aiSummarizationService = aiSummarizationService;
        this.ticketMutationExecutor = ticketMutationExecutor;
        this.ticketAnalysisProducer = ticketAnalysisProducer;
    }

    /**
     * Bir analiz mesajını işler.
     *
     * Nasıl çalışır: kayıt bulunamazsa (silinmiş olabilir) mesaj sessizce
     * tüketilir — yeniden denemenin anlamı yok. Analiz için
     * {@code summarize()} değil {@code analyze()} çağrılır: açıklama, kök
     * neden, çözüm önerileri ve etiketler tek çağrıda gelir.
     *
     * Sonuç yazılırken çözümlerin BİRİKTİRİLMESİ ile DEĞİŞTİRİLMESİ arasındaki
     * fark isteğin kaynağından gelir: aynı hata tekrar görüldüğünde yeni analiz
     * eskilerin YANINA eklenir (farklı görülmelerin farklı ipuçları olabilir),
     * ama kullanıcı "yeniden üret" dediğinde eskiyi beğenmediği için istemiştir
     * — orada biriktirmek listeyi her tıklamada şişirip kaydı okunmaz yapardı.
     *
     * Etiketler her iki durumda da ÜZERİNE YAZILMAZ, birleştirilir: kullanıcının
     * kaydı açarken elle verdiği etiketler kaybolmamalı.
     *
     * @param message kuyruktan gelen analiz isteği; kayıt kimliği, açıklama,
     *                yeniden üretim bayrağı ve deneme sayacını taşır
     */
    @RabbitListener(queues = RabbitConfig.TICKET_ANALYSIS_QUEUE)
    public void handle(TicketAnalysisMessage message) {
        String ticketId = message.getTicketId();
        logger.info("Ticket AI analizi başlıyor. ticketId={}, yenidenÜretim={}, deneme={}",
                ticketId, message.isRegenerate(), message.getAttempt() + 1);

        if (ticketRepository.findById(ticketId).isEmpty()) {
            logger.warn("AI analizi için ticket bulunamadı (silinmiş olabilir). ticketId={}", ticketId);
            return;
        }

        ticketMutationExecutor.mutate(ticketId, ticket -> ticket.setStatus(TicketStatus.PROCESSING));

        boolean regenerate = message.isRegenerate();

        try {
            AIAnalysisResponse analysis = aiSummarizationService.analyze(message.getDescription(), regenerate);

            ticketMutationExecutor.mutate(ticketId, ticket -> {
                ticket.setAiGeneratedDescription(analysis.getDescription());
                ticket.setAiRootCause(analysis.getRootCause());

                if (regenerate) {
                    ticket.setSolutions(new ArrayList<>(analysis.getSolutions()));
                } else {
                    for (String solution : analysis.getSolutions()) {
                        if (!ticket.getSolutions().contains(solution)) {
                            ticket.getSolutions().add(solution);
                        }
                    }
                }

                /*
                 * Yapısal çıktı desteklemeyen bir sağlayıcı aktifse etiket listesi boş
                 * gelir ve mevcut etiketler olduğu gibi kalır — beklenen durum, hata değil.
                 */
                ticket.mergeAiTags(analysis.getTags());
                ticket.setStatus(TicketStatus.COMPLETED);
            });

            logger.info("Ticket AI analizi tamamlandı. ticketId={}, yenidenÜretim={}, çözümSayısı={}, etiketler={}",
                    ticketId, regenerate, analysis.getSolutions().size(), analysis.getTags());
        } catch (AiSummarizationException e) {
            /*
             * AI tarafındaki bir sorun (sağlayıcı kapalı, timeout, kota, bozuk yanıt) bu
             * isteğin ÖLÜMÜ değildir. Eskiden burada sadece FAILED yazılıp metot normal
             * dönüyordu, yani RabbitMQ mesajı ACK'liyordu ve model bir dakika sonra geri
             * gelse bile o kayıt bir daha hiç analiz edilmiyordu.
             */
            handleAiFailure(message, e);
        }
    }

    /**
     * AI hatası sonrası mesajın nereye gideceğine karar verir.
     *
     * Nasıl çalışır: deneme hakkı varsa mesaj bekleme kuyruğuna, yoksa park
     * kuyruğuna gider. İKİ YOLDA DA mesaj silinmez.
     *
     * Bekleme yolunda sıra kritik: ÖNCE yayın, SONRA durum güncellemesi.
     * Bekleme kuyruğuna yazılamazsa buradan hata çıkar, mesaj ACK'lenmez ve
     * RabbitMQ onu geri verir. Ters sırada yapılsaydı, yayın başarısız olduğunda
     * kayıt "tekrar denenecek" görünüp aslında bir daha hiç denenmeyecekti.
     *
     * Bekleme yolunda durum FAILED değil PENDING yazılır: analiz gerçekten
     * kuyrukta bekliyor, vazgeçilmiş değil. FAILED yazmak arayüzde
     * "bitti, olmadı" anlamına gelirdi.
     *
     * @param message işlenemeyen mesajın kendisi (deneme sayacı henüz artırılmamış)
     * @param e       sağlayıcıdan gelen hata; mesajı park sebebi olarak kullanılır
     */
    private void handleAiFailure(TicketAnalysisMessage message, AiSummarizationException e) {
        String ticketId = message.getTicketId();
        TicketAnalysisMessage retryMessage = message.nextAttempt();
        int attempt = retryMessage.getAttempt();
        int maxAttempts = ticketAnalysisProducer.maxRetryAttempts();

        if (attempt > maxAttempts) {
            ticketAnalysisProducer.park(message, e.getMessage());
            ticketMutationExecutor.mutate(ticketId, ticket -> ticket.setStatus(TicketStatus.FAILED));
            logger.error("Ticket AI analizi {} denemede de başarısız oldu, mesaj park edildi. ticketId={}, hata={}",
                    maxAttempts + 1, ticketId, e.getMessage(), e);
            return;
        }

        int delaySeconds = ticketAnalysisProducer.scheduleRetry(retryMessage);

        ticketMutationExecutor.mutate(ticketId, ticket -> ticket.setStatus(TicketStatus.PENDING));

        logger.warn("Ticket AI analizi başarısız oldu, {} sn sonra yeniden denenecek. "
                        + "ticketId={}, deneme={}/{}, hata={}",
                delaySeconds, ticketId, attempt, maxAttempts, e.getMessage(), e);
    }
}
