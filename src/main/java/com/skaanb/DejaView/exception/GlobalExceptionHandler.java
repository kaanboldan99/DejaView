package com.skaanb.DejaView.exception;

import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.model.TicketStatus;
import com.skaanb.DejaView.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Uçlardan dışarı sızan hataları tek noktada karşılayan işleyici.
 *
 * Nasıl çalışır: {@code @ControllerAdvice} sayesinde tüm controller'lar için
 * geçerlidir. İki işleyici var ve Spring EN ÖZEL olanı seçer: profil hataları
 * kendi metoduna, geri kalan her şey catch-all metoduna düşer.
 *
 * Ayrımın sebebi davranış farkı: catch-all yolu hatayı yalnızca loglamakla
 * kalmaz, Elasticsearch'e bir hata KAYDI da yazar — yani uygulamanın kendi
 * hataları da DejaView'da izlenebilir hâle gelir. Kullanıcı hatalarının
 * (çakışan e-posta gibi) bu şekilde kaydedilmesi ise indeksi gereksiz yere
 * kirletirdi.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Sistem hatalarını kayıt olarak indekslemek için kullanılan depo. */
    private final TicketRepository ticketRepository;

    /**
     * @param ticketRepository hata kaydının yazılacağı Elasticsearch deposu
     */
    public GlobalExceptionHandler(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Kullanıcı kaynaklı profil hatalarını 400 olarak yanıtlar.
     *
     * Nasıl çalışır: çakışan e-posta/telefon ya da geçersiz numara biçimi gibi
     * durumlar sistem hatası değildir; bu yüzden Elasticsearch'e kayıt YAZILMAZ
     * ve log seviyesi INFO'da kalır. Ayrı ele alınmasının sebebi aşağıdaki
     * catch-all işleyiciye düşmesini engellemek.
     *
     * @param ex      yakalanan profil hatası; mesajı doğrudan kullanıcıya gider
     * @param request hatanın oluştuğu istek; yolu yanıta eklenir
     * @return 400 ve zaman damgası, durum, hata, mesaj, yol alanlarını taşıyan gövde
     */
    @ExceptionHandler(ProfileUpdateException.class)
    public ResponseEntity<Object> handleProfileUpdate(ProfileUpdateException ex, HttpServletRequest request) {
        logger.info("Profil güncelleme reddedildi. path={} | sebep={}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest().body(Map.of(
                "timestamp", LocalDateTime.now(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Bad Request",
                "message", ex.getMessage(),
                "path", request.getRequestURI()
        ));
    }

    /**
     * Beklenmedik tüm hataları 500 olarak yanıtlar ve hatayı kayıt olarak indeksler.
     *
     * Nasıl çalışır: sırasıyla (1) yığın izi metne çevrilir; (2) hata loglanır;
     * (3) hatadan bir {@link TicketDocument} üretilip Elasticsearch'e yazılır;
     * (4) istemciye ayrıntı içermeyen genel bir 500 gövdesi döner.
     *
     * İndeksleme kendi {@code try/catch} bloğu içinde: Elasticsearch'e
     * ulaşılamaması, kullanıcıya dönecek yanıtı da engellememeli. Bu yüzden
     * oradaki hata yalnızca loglanır ve akış devam eder.
     *
     * Yanıtta hata mesajı ya da yığın izi YOK — iç detayların dışarı sızmaması
     * için; bunlar loga ve arama motoruna gider.
     *
     * @param ex      yakalanan hata
     * @param request hatanın oluştuğu istek; yolu hem başlıkta hem yanıtta kullanılır
     * @return 500 ve genel hata gövdesi
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllExceptions(Exception ex, HttpServletRequest request) {

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        String stackTraceStr = sw.toString();

        logger.error("[DejaView Error] Path: {} | Message: {}", request.getRequestURI(), ex.getMessage(), ex);

        try {
            TicketDocument ticket = new TicketDocument();
            String title = ex.getClass().getSimpleName() + ": " + request.getRequestURI();
            ticket.setTitle(title);
            ticket.setTitleNormalized(title.toLowerCase());
            ticket.setErrorMessage(ex.getMessage());
            ticket.setStackTrace(stackTraceStr);
            ticket.setServiceName("DejaView-Backend");

            ticket.setCreatedAt(Instant.now());
            ticket.setLastOccurrenceAt(Instant.now());

            /* Bu yol AI kuyruğuna girmiyor; kayıt doğrudan COMPLETED yazılıyor. */
            ticket.setAiGeneratedDescription("AI Analizi Devre Dışı");
            ticket.setAiTags(java.util.List.of("SystemError"));
            ticket.getSolutions().add("Çözüm adımları henüz eklenmedi.");
            ticket.setCreatedBy("system");
            ticket.setStatus(TicketStatus.COMPLETED);

            ticketRepository.save(ticket);
            logger.info("[DejaView Elasticsearch] Hata başarılı bir şekilde indekslendi: {}", ex.getMessage());

        } catch (Exception elasticsearchException) {
            logger.error("[DejaView Elasticsearch Error] Hata Elasticsearch'e kaydedilemedi!", elasticsearchException);
        }

        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now(),
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error", "Internal Server Error",
                "message", "Sistemde bir hata oluştu. Detaylar log sistemine ve arama motoruna işlendi.",
                "path", request.getRequestURI()
        );

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
