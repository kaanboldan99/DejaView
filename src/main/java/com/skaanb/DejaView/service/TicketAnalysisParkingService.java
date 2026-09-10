package com.skaanb.DejaView.service;

import com.skaanb.DejaView.config.RabbitConfig;
import com.skaanb.DejaView.dto.TicketAnalysisMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Park kuyruğunda bekleyen analiz isteklerini görüntüler ve geri oynatır.
 *
 * Nasıl çalışır: park kuyruğunun bilinçli olarak TÜKETİCİSİ YOKTUR — deneme
 * hakkı tükenen mesaj oraya alınıp SİLİNMEDEN bekler. Ama "silinmiyor", ancak
 * geri alınabiliyorsa bir şey ifade eder: yerel model tekrar ayağa
 * kalktığında ya da API kotası yenilendiğinde tek bir admin çağrısıyla bekleyen
 * tüm analizler yeniden kuyruğa girer.
 *
 * İki uç sunar: kaç mesaj beklediği ve hepsinin geri oynatılması
 * (bkz. {@link com.skaanb.DejaView.controller.TicketController}).
 */
@Service
public class TicketAnalysisParkingService {

    private static final Logger logger = LoggerFactory.getLogger(TicketAnalysisParkingService.class);

    /**
     * Tek çağrıda ele alınacak azami mesaj sayısı.
     *
     * Nasıl çalışır: sınır iki işe yarıyor — binlerce mesajı tek HTTP isteğinde
     * işlemeye çalışıp zaman aşımına düşmüyoruz, ve dönüştürülemeyip kuyruğa
     * geri konan mesajlar sonsuz döngüye sebep olmuyor (geri konan mesaj da bu
     * sayaçtan düşüyor). Kalan varsa uç tekrar çağrılır.
     */
    private static final int MAX_REPLAY_BATCH = 500;

    /** Kuyruktan okuma ve kuyruğa yazma işlemlerini yapan şablon. */
    private final RabbitTemplate rabbitTemplate;

    /**
     * @param rabbitTemplate kuyruk okuma/yazma şablonu
     */
    public TicketAnalysisParkingService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Park kuyruğunda kaç mesaj beklediğini döner.
     *
     * Nasıl çalışır: mesajları okumadan, doğrudan kanal üzerinden kuyruk
     * sayacına bakar; yani bu çağrı kuyruğun içeriğini DEĞİŞTİRMEZ.
     *
     * @return bekleyen mesaj sayısı; sayı okunamazsa 0
     */
    public int parkedCount() {
        Integer count = rabbitTemplate.execute(channel ->
                (int) channel.messageCount(RabbitConfig.TICKET_PARKED_QUEUE));
        return count == null ? 0 : count;
    }

    /**
     * Park kuyruğundaki mesajları analiz kuyruğuna geri koyar.
     *
     * Nasıl çalışır: kuyruk, boşalana ya da {@value #MAX_REPLAY_BATCH} sınırına
     * ulaşılana kadar tek tek okunur. Her mesaj için:
     *
     * - Gövdesi çözülebiliyorsa deneme sayacı SIFIRLANIR ve mesaj analiz
     *   kuyruğuna yayınlanır. Sıfırlama bilinçli: geri oynatma kararını bir
     *   insan verdi ve muhtemelen arızayı da giderdi; mesajın tükenmiş sayaçla
     *   gelip ilk hatada yeniden park edilmesi bu kararı anlamsız kılardı.
     *
     * - Gövdesi çözülemiyorsa (örn. eski bir biçimden kalma) mesaj OLDUĞU GİBİ
     *   park kuyruğuna geri konur ve atlanmış sayılır. Okunamayan bir mesajı
     *   yutmak, park kuyruğunun varlık sebebine aykırı olurdu.
     *
     * @return geri oynatılan ve atlanan mesaj sayıları
     */
    public ReplayResult replayAll() {
        int replayed = 0;
        int skipped = 0;

        for (int i = 0; i < MAX_REPLAY_BATCH; i++) {
            Message raw = rabbitTemplate.receive(RabbitConfig.TICKET_PARKED_QUEUE);
            if (raw == null) {
                break;
            }

            TicketAnalysisMessage message;
            try {
                message = (TicketAnalysisMessage) rabbitTemplate.getMessageConverter().fromMessage(raw);
            } catch (Exception e) {
                rabbitTemplate.send(RabbitConfig.TICKET_PARKED_EXCHANGE,
                        RabbitConfig.TICKET_PARKED_ROUTING_KEY, raw);
                skipped++;
                logger.warn("Park kuyruğundaki mesaj çözümlenemedi, kuyruğa geri kondu. hata={}", e.getMessage());
                continue;
            }

            message.setAttempt(0);

            rabbitTemplate.convertAndSend(
                    RabbitConfig.TICKET_ANALYSIS_EXCHANGE,
                    RabbitConfig.TICKET_ANALYSIS_ROUTING_KEY,
                    message
            );
            replayed++;
            logger.info("Park edilmiş analiz isteği yeniden kuyruğa alındı. ticketId={}", message.getTicketId());
        }

        logger.info("Park kuyruğu geri oynatıldı. geriOynatılan={}, atlanan={}", replayed, skipped);
        return new ReplayResult(replayed, skipped);
    }

    /**
     * Geri oynatma sonucunu taşıyan kayıt tipi.
     *
     * @param replayed analiz kuyruğuna başarıyla geri konan mesaj sayısı
     * @param skipped  gövdesi çözülemediği için park kuyruğunda bırakılan mesaj sayısı
     */
    public record ReplayResult(int replayed, int skipped) {
    }
}
