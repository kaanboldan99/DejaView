package com.skaanb.DejaView.service;

import com.skaanb.DejaView.config.RabbitConfig;
import com.skaanb.DejaView.dto.TicketAnalysisMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Analiz isteklerini RabbitMQ kuyruklarına yazan üretici.
 *
 * Nasıl çalışır: gerçek analiz {@link TicketAnalysisListener} tarafında, arka
 * planda yapılır; bu sınıf yalnızca mesajı doğru kuyruğa koyar. Bir analiz
 * isteğinin kuyruğa girebileceği ÜÇ yol da burada toplanmıştır:
 *
 * 1) {@link #enqueueAnalysis(String, String, boolean)} — ilk kez (ya da yeni
 *    bir görülme sonrası) analiz.
 * 2) {@link #scheduleRetry(TicketAnalysisMessage)} — AI hata verdiği için
 *    bekletilip yeniden deneme.
 * 3) {@link #park(TicketAnalysisMessage, String)} — deneme hakkı bittiğinde
 *    silmek yerine park etme.
 *
 * Hata davranışı üç yolda AYNI DEĞİL, bilinçli olarak farklı: ilk yolda hata
 * yutulur (kullanıcı isteği bundan etkilenmemeli), yeniden deneme yolunda ise
 * yutulmaz (bkz. ilgili metot).
 */
@Service
public class TicketAnalysisProducer {

    private static final Logger logger = LoggerFactory.getLogger(TicketAnalysisProducer.class);

    /** Mesajları exchange'lere yayınlayan şablon. */
    private final RabbitTemplate rabbitTemplate;

    /**
     * Kaçıncı denemede ne kadar bekleneceği (saniye).
     *
     * Nasıl çalışır: liste uzunluğu aynı zamanda deneme hakkını belirler —
     * {@code 60,300,1800} demek, ilk hatadan sonra 1 dk, sonra 5 dk, sonra
     * 30 dk beklenir; dördüncü hatada mesaj park kuyruğuna alınır demektir.
     *
     * Artan gecikme bilinçli: yerel modelin kapalı olması gibi tipik arızalar
     * dakikalar sürüyor. Sabit kısa aralıkla denemek hem kuyruğu hem logu
     * şişirir, tek bir uzun bekleme ise saniyeler süren bir kesintiden sonra
     * kaydı gereksiz yere bekletirdi.
     */
    private final List<Integer> retryDelaysSeconds;

    /**
     * Bağımlılıkları alır ve gecikme merdivenini ayrıştırır.
     *
     * Nasıl çalışır: ayrıştırma {@link RabbitConfig#parseRetryDelaysSeconds}
     * ile yapılır, yani kuyruk topolojisiyle AYNI listeyi okur — ikisinin
     * ayrışması, var olmayan bir gecikme kuyruğuna mesaj yollamak demek olurdu.
     *
     * @param rabbitTemplate mesaj yayın şablonu
     * @param rawDelays      {@code dejaview.ai.retry.delays-seconds} ham değeri
     * @throws IllegalArgumentException gecikme listesi bozuksa (uygulama açılmaz)
     */
    public TicketAnalysisProducer(RabbitTemplate rabbitTemplate,
                                  @Value("${dejaview.ai.retry.delays-seconds:60,300,1800}") String rawDelays) {
        this.rabbitTemplate = rabbitTemplate;
        this.retryDelaysSeconds = RabbitConfig.parseRetryDelaysSeconds(rawDelays);
    }

    /**
     * Normal (yeniden üretim olmayan) bir analiz isteğini kuyruğa koyar.
     *
     * @param ticketId    analiz edilecek kaydın doküman kimliği
     * @param description modele verilecek hata açıklaması
     */
    public void enqueueAnalysis(String ticketId, String description) {
        enqueueAnalysis(ticketId, description, false);
    }

    /**
     * Analiz isteğini kuyruğa koyar.
     *
     * Nasıl çalışır: {@code regenerate=true} yalnızca kullanıcının açık
     * "yeniden üret" isteğinde kullanılır; sonucun mevcut çözümlere eklenmek
     * yerine onların yerine geçmesini sağlar (bkz. {@link TicketAnalysisListener}).
     *
     * Yayın hatası BURADA YUTULUR: RabbitMQ'ya ulaşılamıyorsa kayıt zaten
     * PENDING durumunda kaydedilmiş olur ve kullanıcının isteği bu yüzden
     * başarısız olmamalıdır. Hata yalnızca loglanır.
     *
     * @param ticketId    analiz edilecek kaydın doküman kimliği
     * @param description modele verilecek hata açıklaması
     * @param regenerate  {@code true} ise kullanıcının açık "yeniden üret" isteği
     */
    public void enqueueAnalysis(String ticketId, String description, boolean regenerate) {
        try {
            TicketAnalysisMessage message = new TicketAnalysisMessage(ticketId, description, regenerate);
            rabbitTemplate.convertAndSend(
                    RabbitConfig.TICKET_ANALYSIS_EXCHANGE,
                    RabbitConfig.TICKET_ANALYSIS_ROUTING_KEY,
                    message
            );
            logger.info("Ticket AI analiz kuyruğuna eklendi. ticketId={}, yenidenÜretim={}", ticketId, regenerate);
        } catch (Exception e) {
            logger.error("Ticket kuyruğa eklenemedi (RabbitMQ'ya ulaşılamıyor olabilir). ticketId={}, hata={}",
                    ticketId, e.getMessage(), e);
        }
    }

    /**
     * Toplam yeniden deneme hakkını döner.
     *
     * Nasıl çalışır: gecikme listesinin uzunluğudur; ilk deneme buna DÂHİL
     * DEĞİLDİR. Dinleyici bu sayıyı, mesajı park edip etmeyeceğine karar
     * vermek için kullanır.
     *
     * @return tanımlı yeniden deneme sayısı
     */
    public int maxRetryAttempts() {
        return retryDelaysSeconds.size();
    }

    /**
     * Mesajı, deneme numarasına karşılık gelen bekleme kuyruğuna koyar.
     *
     * Nasıl çalışır: bekleme kuyruğunun tüketicisi yoktur; TTL dolunca mesaj
     * dead-letter yoluyla analiz kuyruğuna kendiliğinden geri düşer
     * (bkz. {@link RabbitConfig}).
     *
     * {@link #enqueueAnalysis} metodunun aksine hata BURADA YUTULMAZ: yayın
     * başarısızsa çağıran taraf (dinleyici) mesajı ACK'lememelidir ki RabbitMQ
     * onu geri versin. Yutulsaydı, "mesaj kaybolmasın" diye eklenen yol tam da
     * mesajı kaybettiğimiz yer olurdu.
     *
     * @param message deneme numarası ARTIRILMIŞ mesaj (bkz.
     *                {@link TicketAnalysisMessage#nextAttempt()})
     * @return bu denemede beklenecek süre (saniye)
     * @throws IllegalArgumentException deneme numarası tanımlı aralığın dışındaysa
     */
    public int scheduleRetry(TicketAnalysisMessage message) {
        int attempt = message.getAttempt();
        if (attempt < 1 || attempt > retryDelaysSeconds.size()) {
            throw new IllegalArgumentException("Geçersiz deneme numarası: " + attempt
                    + " (tanımlı deneme sayısı: " + retryDelaysSeconds.size() + ")");
        }

        int delaySeconds = retryDelaysSeconds.get(attempt - 1);
        rabbitTemplate.convertAndSend(
                RabbitConfig.TICKET_RETRY_EXCHANGE,
                RabbitConfig.delayRoutingKey(delaySeconds),
                message
        );
        logger.info("Ticket AI analizi {} sn bekletilecek. ticketId={}, deneme={}/{}",
                delaySeconds, message.getTicketId(), attempt, retryDelaysSeconds.size());
        return delaySeconds;
    }

    /**
     * Deneme hakkı bittiğinde mesajı park kuyruğuna alır.
     *
     * Nasıl çalışır: park kuyruğunun tüketicisi yoktur; mesaj silinmez, orada
     * durur ve bir admin geri oynatabilir
     * (bkz. {@link TicketAnalysisParkingService}).
     *
     * Park sebebi ve deneme sayısı mesaj başlıklarına ({@code x-park-reason},
     * {@code x-park-attempts}) yazılır: kuyruğa bakan kişi logu aramak zorunda
     * kalmasın diye.
     *
     * @param message park edilecek mesaj
     * @param reason  park sebebi; genelde AI hatasının mesajı
     */
    public void park(TicketAnalysisMessage message, String reason) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.TICKET_PARKED_EXCHANGE,
                RabbitConfig.TICKET_PARKED_ROUTING_KEY,
                message,
                m -> {
                    m.getMessageProperties().setHeader("x-park-reason", reason);
                    m.getMessageProperties().setHeader("x-park-attempts", message.getAttempt());
                    return m;
                }
        );
        logger.error("Ticket AI analizi park kuyruğuna alındı (SİLİNMEDİ). ticketId={}, deneme={}, sebep={}",
                message.getTicketId(), message.getAttempt(), reason);
    }
}
