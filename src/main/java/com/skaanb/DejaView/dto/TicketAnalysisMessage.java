package com.skaanb.DejaView.dto;

import java.io.Serializable;

/**
 * RabbitMQ üzerinden AI analiz kuyruğuna gönderilen mesaj.
 *
 * Nasıl çalışır: {@code Jackson2JsonMessageConverter} ile JSON'a serialize
 * edilir; yani mesajın alanları kuyrukta duran verinin ta kendisidir. Bu yüzden
 * alan eklerken/çıkarırken kuyrukta bekleyen ESKİ mesajların da hâlâ
 * ayrıştırılabilmesi gerekir — aşağıdaki alanların varsayılan değerleri bunun
 * için var.
 *
 * Mesajı kuyruğa koyan taraf
 * {@link com.skaanb.DejaView.service.TicketAnalysisProducer}, tüketen taraf
 * {@link com.skaanb.DejaView.service.TicketAnalysisListener}.
 */
public class TicketAnalysisMessage implements Serializable {

    /** Analiz edilecek kaydın Elasticsearch doküman kimliği. */
    private String ticketId;

    /** Modele girdi olarak verilecek hata açıklaması. */
    private String description;

    /**
     * İsteğin kaynağı: normal analiz mi, kullanıcının açık "yeniden üret" isteği mi.
     *
     * Nasıl çalışır: kayıt ilk kez (ya da yeni bir görülme sonrası) analiz
     * ediliyorsa {@code false}, kullanıcı "yeniden üret" dediyse {@code true}.
     * İki yol farklı davranır: yeniden üretimde sonuç mevcut çözümlerin ÜZERİNE
     * yazılır (bkz. {@link com.skaanb.DejaView.service.TicketAnalysisListener})
     * ve sağlayıcıdan farklı bir bakış açısı istenir (bkz.
     * {@link com.skaanb.DejaView.service.OpenAiService}).
     *
     * Kuyrukta bu alanı taşımayan eski mesajlar da ayrıştırılabilsin diye
     * varsayılan {@code false}.
     */
    private boolean regenerate;

    /**
     * Bu mesajın kaçıncı denemesi olduğu; 0 = ilk kez kuyruğa girdi.
     *
     * Nasıl çalışır: AI sağlayıcısı hata verdiğinde mesaj SİLİNMEZ, bu sayaç bir
     * artırılıp gecikme kuyruğuna konur (bkz.
     * {@link com.skaanb.DejaView.service.TicketAnalysisProducer#scheduleRetry})
     * ve TTL dolunca analiz kuyruğuna geri düşer.
     *
     * Sayaç mesajın kendisinde taşınıyor çünkü bekleme RabbitMQ üzerinde
     * gerçekleşiyor; uygulama yeniden başlasa bile deneme geçmişi kaybolmuyor.
     * Kuyrukta bu alanı taşımayan eski mesajlar varsayılan 0 ile okunur, yani
     * tam deneme hakkıyla işlenirler.
     */
    private int attempt;

    /**
     * JSON ayrıştırıcının gerektirdiği parametresiz kurucu.
     */
    public TicketAnalysisMessage() {
    }

    /**
     * Normal (yeniden üretim olmayan) bir analiz isteği oluşturur.
     *
     * @param ticketId    analiz edilecek kaydın doküman kimliği
     * @param description modele verilecek hata açıklaması
     */
    public TicketAnalysisMessage(String ticketId, String description) {
        this(ticketId, description, false);
    }

    /**
     * Analiz isteği oluşturur.
     *
     * @param ticketId    analiz edilecek kaydın doküman kimliği
     * @param description modele verilecek hata açıklaması
     * @param regenerate  {@code true} ise kullanıcının açık "yeniden üret"
     *                    isteği; sonuç mevcut çözümlerin yerine geçer
     */
    public TicketAnalysisMessage(String ticketId, String description, boolean regenerate) {
        this.ticketId = ticketId;
        this.description = description;
        this.regenerate = regenerate;
    }

    /**
     * Aynı isteğin bir sonraki deneme numarasıyla kopyasını üretir.
     *
     * Nasıl çalışır: mevcut nesne değiştirilmez, YENİ bir nesne döner. Sebep:
     * tüketilmekte olan mesajın üzerinde oynamak, hata yolunda hangi denemenin
     * loglandığını belirsizleştirirdi.
     *
     * @return kimlik, açıklama ve yeniden üretim bayrağı aynı; {@code attempt}
     *         bir fazla olan yeni mesaj
     */
    public TicketAnalysisMessage nextAttempt() {
        TicketAnalysisMessage next = new TicketAnalysisMessage(ticketId, description, regenerate);
        next.setAttempt(attempt + 1);
        return next;
    }

    /* --- Erişimciler: alanların anlamı yukarıdaki tanımlarda belgelendi, --- */
    /* --- bu metotların okuma/yazma dışında bir davranışı yoktur.         --- */

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public boolean isRegenerate() {
        return regenerate;
    }

    public void setRegenerate(boolean regenerate) {
        this.regenerate = regenerate;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
