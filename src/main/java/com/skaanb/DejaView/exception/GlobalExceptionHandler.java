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

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final TicketRepository ticketRepository;

    public GlobalExceptionHandler(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Kullanıcı kaynaklı profil hataları (çakışan e-posta/telefon, geçersiz
     * numara). Sistem hatası olmadığı için Elasticsearch'e ticket yazılmaz;
     * aşağıdaki catch-all handler'a düşmesin diye ayrı ele alınır.
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

            ticket.setAiGeneratedDescription("AI Analizi Devre Dışı");
            ticket.setAiTags(java.util.List.of("SystemError"));
            ticket.getSolutions().add("Çözüm adımları henüz eklenmedi.");
            ticket.setCreatedBy("system");
            ticket.setStatus(TicketStatus.COMPLETED);

            // HATA 2 ÇÖZÜMÜ: Repository'nin kabul ettiği doğru nesne gönderildi
            ticketRepository.save(ticket);
            logger.info("[DejaView Elasticsearch] Hata başarılı bir şekilde indekslendi: {}", ex.getMessage());

        } catch (Exception elasticsearchException) {
            logger.error("[DejaView Elasticsearch Error] Hata Elasticsearch'e kaydedilemedi!", elasticsearchException);
        }

        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now(), // Yanıt nesnesinde kalabilir veya Instant yapılabilir
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error", "Internal Server Error",
                "message", "Sistemde bir hata oluştu. Detaylar log sistemine ve arama motoruna işlendi.",
                "path", request.getRequestURI()
        );

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}