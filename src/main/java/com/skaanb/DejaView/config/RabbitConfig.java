package com.skaanb.DejaView.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * RabbitMQ kuyruk topolojisi: analiz, bekletme ve park kuyrukları.
 *
 * Nasıl çalışır: burada tanımlanan bean'ler uygulama açılışında RabbitMQ'da
 * karşılıklarını oluşturur (varsa dokunmaz). Üç kuyruk ailesi var:
 *
 * 1) Analiz kuyruğu: asıl iş kuyruğu; üretici buraya yazar, dinleyici buradan
 *    tüketir.
 *
 * 2) Bekletme (retry) kuyrukları: TÜKETİCİSİ YOK, yalnızca {@code x-message-ttl}
 *    ile mesajı bekletir. TTL dolunca mesaj dead-letter yoluyla ana exchange'e,
 *    yani analiz kuyruğuna geri düşer. Bekleyen mesaj tüketici thread'ini
 *    meşgul etmez; sıradaki kayıtlar işlenmeye devam eder ({@code Thread.sleep}
 *    ile beklemenin aksine).
 *
 * 3) Park kuyruğu: son denemede de başarısız olan mesajlar buraya alınır.
 *    Tüketicisi yoktur; mesaj burada süresiz durur, silinmez. Admin bir uçtan
 *    geri oynatabilir
 *    (bkz. {@link com.skaanb.DejaView.service.TicketAnalysisParkingService}).
 *
 * Bu yapının sebebi: AI sağlayıcısına ulaşılamadığında (yerel model kapalı,
 * zaman aşımı, 429, 5xx) analiz isteğinin KAYBOLMAMASI gerekiyor. Eskiden
 * dinleyici hatayı yutup kaydı FAILED yapıyordu ve normal döndüğü için RabbitMQ
 * mesajı ACK'liyordu — yani model birkaç dakika sonra geri gelse bile o kayıt
 * bir daha hiç denenmiyordu.
 */
@Configuration
public class RabbitConfig {

    /** Analiz mesajlarının yayınlandığı ana exchange. */
    public static final String TICKET_ANALYSIS_EXCHANGE = "dejaview.tickets.exchange";

    /** Dinleyicinin tükettiği asıl analiz kuyruğu. */
    public static final String TICKET_ANALYSIS_QUEUE = "dejaview.tickets.analysis.queue";

    /** Analiz kuyruğunu ana exchange'e bağlayan yönlendirme anahtarı. */
    public static final String TICKET_ANALYSIS_ROUTING_KEY = "ticket.analysis";

    /** Bekletme kuyruklarının bağlı olduğu exchange. */
    public static final String TICKET_RETRY_EXCHANGE = "dejaview.tickets.retry.exchange";

    /** Park kuyruğunun bağlı olduğu exchange. */
    public static final String TICKET_PARKED_EXCHANGE = "dejaview.tickets.parked.exchange";

    /** Deneme hakkı tükenen mesajların silinmek yerine beklediği kuyruk. */
    public static final String TICKET_PARKED_QUEUE = "dejaview.tickets.analysis.parked.queue";

    /** Park kuyruğunu kendi exchange'ine bağlayan yönlendirme anahtarı. */
    public static final String TICKET_PARKED_ROUTING_KEY = "ticket.analysis.parked";

    /**
     * Belirli bir bekleme süresine karşılık gelen gecikme kuyruğunun adını üretir.
     *
     * Nasıl çalışır: kuyruk adı süreyi İÇERİR. Sebebi: bir kuyruğun TTL'i
     * sonradan değiştirilemez (RabbitMQ farklı argümanlarla yeniden tanımlamayı
     * {@code PRECONDITION_FAILED} ile reddeder). Süre değişince adı da değiştiği
     * için yeni bir kuyruk açılır, eskisiyle çakışmaz.
     *
     * @param seconds bekleme süresi (saniye)
     * @return o süreye ait gecikme kuyruğunun adı
     */
    public static String delayQueueName(int seconds) {
        return "dejaview.tickets.analysis.delay." + seconds + "s";
    }

    /**
     * Belirli bir bekleme süresine karşılık gelen yönlendirme anahtarını üretir.
     *
     * @param seconds bekleme süresi (saniye)
     * @return o süredeki gecikme kuyruğuna yönlendiren anahtar
     */
    public static String delayRoutingKey(int seconds) {
        return "ticket.analysis.delay." + seconds + "s";
    }

    /**
     * Yapılandırmadaki gecikme listesini ({@code "60,300,1800"}) sayı listesine çevirir.
     *
     * Nasıl çalışır: virgülle ayrılmış değerler tek tek ayrıştırılır, boş
     * parçalar atlanır. Bozuk bir değerde SESSİZCE varsayılana DÜŞÜLMEZ:
     * yanlış yazılmış bir gecikme listesi, fark edilmeyen bir "hiç bekleme"
     * davranışına dönüşürdü. Bunun yerine uygulama açılışta patlar ve neyin
     * yanlış olduğu görünür olur.
     *
     * @param raw ham ayar değeri; {@code null} veya boş olabilir
     * @return değiştirilemez süre listesi (saniye); girdi boşsa boş liste
     * @throws IllegalArgumentException sayıya çevrilemeyen ya da pozitif
     *                                  olmayan bir değer varsa
     */
    public static List<Integer> parseRetryDelaysSeconds(String raw) {
        List<Integer> delays = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int seconds;
            try {
                seconds = Integer.parseInt(trimmed);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "dejaview.ai.retry.delays-seconds sayı listesi olmalı (örn. 60,300,1800). Geçersiz değer: " + trimmed, e);
            }
            if (seconds <= 0) {
                throw new IllegalArgumentException(
                        "dejaview.ai.retry.delays-seconds içindeki süreler pozitif olmalı. Geçersiz değer: " + trimmed);
            }
            delays.add(seconds);
        }
        return List.copyOf(delays);
    }

    /**
     * Analiz mesajlarının yayınlandığı ana exchange'i tanımlar.
     *
     * Nasıl çalışır: {@code DirectExchange} yönlendirme anahtarını birebir
     * eşleştirir; kalıcı ({@code durable}) olduğu için RabbitMQ yeniden
     * başlasa da tanım kaybolmaz.
     *
     * @return ana analiz exchange'i
     */
    @Bean
    public DirectExchange ticketAnalysisExchange() {
        return new DirectExchange(TICKET_ANALYSIS_EXCHANGE, true, false);
    }

    /**
     * Dinleyicinin tükettiği analiz kuyruğunu tanımlar.
     *
     * Nasıl çalışır: {@code durable=true} verildiği için RabbitMQ yeniden
     * başlasa bile kuyruktaki mesajlar kaybolmaz.
     *
     * @return kalıcı analiz kuyruğu
     */
    @Bean
    public Queue ticketAnalysisQueue() {
        return new Queue(TICKET_ANALYSIS_QUEUE, true);
    }

    /**
     * Analiz kuyruğunu ana exchange'e bağlar.
     *
     * @param ticketAnalysisQueue    bağlanacak kuyruk
     * @param ticketAnalysisExchange bağlanılacak exchange
     * @return kuyruk ile exchange arasındaki bağ
     */
    @Bean
    public Binding ticketAnalysisBinding(Queue ticketAnalysisQueue, DirectExchange ticketAnalysisExchange) {
        return BindingBuilder.bind(ticketAnalysisQueue)
                .to(ticketAnalysisExchange)
                .with(TICKET_ANALYSIS_ROUTING_KEY);
    }

    /**
     * Yapılandırmadaki her gecikme için birer bekleme kuyruğu ve bağını tanımlar.
     *
     * Nasıl çalışır: kuyruklar tüketilmez; tek işlevleri mesajı TTL kadar tutup
     * ana exchange'e geri dead-letter'lamaktır. Tüm tanımlar tek bir
     * {@link Declarables} bean'i olarak döner çünkü sayıları yapılandırmaya
     * bağlı, yani derleme zamanında bilinmiyor.
     *
     * Ana kuyruğun argümanlarına DOKUNULMUYOR: mevcut kurulumlarda o kuyruk
     * zaten argümansız oluşturulmuş durumda ve farklı argümanlarla yeniden
     * tanımlamak {@code PRECONDITION_FAILED} verir; kuyruğu elle silmek de
     * içindeki bekleyen analizleri yok ederdi. Bu yüzden bekletme, ana kuyruğun
     * dead-letter ayarıyla değil, dinleyicinin açık yayınıyla yapılıyor
     * (bkz. {@link com.skaanb.DejaView.service.TicketAnalysisProducer#scheduleRetry}).
     *
     * @param rawDelays {@code dejaview.ai.retry.delays-seconds} ayarının ham
     *                  değeri; varsayılan {@code 60,300,1800}
     * @return bekletme exchange'i, gecikme kuyrukları ve bağları
     */
    @Bean
    public Declarables ticketAnalysisRetryTopology(
            @Value("${dejaview.ai.retry.delays-seconds:60,300,1800}") String rawDelays) {

        List<Declarable> declarables = new ArrayList<>();

        DirectExchange retryExchange = new DirectExchange(TICKET_RETRY_EXCHANGE, true, false);
        declarables.add(retryExchange);

        for (int seconds : parseRetryDelaysSeconds(rawDelays)) {
            Queue delayQueue = QueueBuilder.durable(delayQueueName(seconds))
                    .ttl(seconds * 1000)
                    .deadLetterExchange(TICKET_ANALYSIS_EXCHANGE)
                    .deadLetterRoutingKey(TICKET_ANALYSIS_ROUTING_KEY)
                    .build();
            declarables.add(delayQueue);
            declarables.add(BindingBuilder.bind(delayQueue).to(retryExchange).with(delayRoutingKey(seconds)));
        }

        return new Declarables(declarables);
    }

    /**
     * Park kuyruğunun bağlı olduğu exchange'i tanımlar.
     *
     * @return park exchange'i
     */
    @Bean
    public DirectExchange ticketParkedExchange() {
        return new DirectExchange(TICKET_PARKED_EXCHANGE, true, false);
    }

    /**
     * Park kuyruğunu tanımlar.
     *
     * Nasıl çalışır: TTL verilmez — park edilen mesaj kendiliğinden silinmesin,
     * birileri bakana kadar dursun diye.
     *
     * @return süresiz bekleyen, kalıcı park kuyruğu
     */
    @Bean
    public Queue ticketParkedQueue() {
        return QueueBuilder.durable(TICKET_PARKED_QUEUE).build();
    }

    /**
     * Park kuyruğunu kendi exchange'ine bağlar.
     *
     * @param ticketParkedQueue    bağlanacak park kuyruğu
     * @param ticketParkedExchange bağlanılacak park exchange'i
     * @return kuyruk ile exchange arasındaki bağ
     */
    @Bean
    public Binding ticketParkedBinding(Queue ticketParkedQueue, DirectExchange ticketParkedExchange) {
        return BindingBuilder.bind(ticketParkedQueue)
                .to(ticketParkedExchange)
                .with(TICKET_PARKED_ROUTING_KEY);
    }

    /**
     * AI hatası DIŞINDAKİ beklenmedik hatalar için son emniyet mekanizmasını tanımlar.
     *
     * Nasıl çalışır: Elasticsearch'e yazılamaması, bozuk mesaj gövdesi gibi
     * beklenmedik hatalarda Spring'in retry mekanizması denemelerini tüketince
     * mesajı requeue etmeden reddeder ve
     * {@code spring.rabbitmq.listener.simple.default-requeue-rejected=false}
     * olduğu için mesaj SİLİNİRDİ. Bu bean sayesinde silinmek yerine park
     * kuyruğuna yayınlanır — üstelik hatanın kendisi {@code x-exception-*}
     * başlıklarında taşınır, yani kuyruğa bakan kişi logu aramak zorunda kalmaz.
     *
     * Spring Boot, retry açıkken tekil {@link MessageRecoverer} bean'ini
     * kendiliğinden devreye alır; ayrıca bağlamaya gerek yoktur.
     *
     * @param rabbitTemplate mesajı park exchange'ine yeniden yayınlayacak şablon
     * @return mesajı silmek yerine park kuyruğuna yayınlayan kurtarıcı
     */
    @Bean
    public MessageRecoverer ticketAnalysisMessageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, TICKET_PARKED_EXCHANGE, TICKET_PARKED_ROUTING_KEY);
    }

    /**
     * Mesajların JSON olarak serialize edilmesini sağlayan dönüştürücüyü tanımlar.
     *
     * Nasıl çalışır: {@link com.skaanb.DejaView.dto.TicketAnalysisMessage}
     * nesneleri kuyruğa JSON olarak yazılır. Java serileştirmesi yerine JSON
     * kullanılması, kuyrukta bekleyen mesajların sınıf değişikliklerine karşı
     * daha dayanıklı olmasını sağlar.
     *
     * @return JSON mesaj dönüştürücüsü
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Mesaj yayınlamakta kullanılan şablonu kurar.
     *
     * @param connectionFactory     RabbitMQ bağlantı üreticisi (Spring Boot sağlar)
     * @param jsonMessageConverter  nesneleri JSON'a çeviren dönüştürücü
     * @return JSON dönüştürücüsü bağlanmış yayın şablonu
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
