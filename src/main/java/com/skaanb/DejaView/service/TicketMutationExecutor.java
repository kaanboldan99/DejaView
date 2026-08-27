package com.skaanb.DejaView.service;

import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

// Tek Sorumluluk: bir TicketDocument üzerinde "oku -> değiştir -> kaydet" işlemini
// concurrent güncellemelere karşı güvenli şekilde yapmak. RabbitMQ dinleyicisi birden
// fazla thread ile çalıştığında (bkz. application.properties: listener.simple.concurrency)
// veya bir kullanıcı ticket oluştururken aynı anda başka bir occurrence/AI sonucu aynı
// dokümana yazmaya çalışırsa, TicketDocument@Version sayesinde eski versiyonla yapılan
// save() OptimisticLockingFailureException fırlatır; bu sınıf böyle bir çakışmada
// dokümanı yeniden okuyup mutator'ı tekrar uygulayarak "lost update" oluşmasını engeller.
@Component
public class TicketMutationExecutor {

    private static final Logger logger = LoggerFactory.getLogger(TicketMutationExecutor.class);
    private static final int MAX_ATTEMPTS = 5;

    private final TicketRepository ticketRepository;

    public TicketMutationExecutor(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * id'si verilen ticket'ı okur, mutator ile değiştirir ve kaydeder. Concurrent bir
     * güncelleme çakışması olursa (OptimisticLockingFailureException) dokümanı en güncel
     * haliyle yeniden okuyup mutator'ı tekrar uygular.
     *
     * @throws IllegalArgumentException ticket bulunamazsa
     * @throws IllegalStateException MAX_ATTEMPTS denemede de çakışma çözülemezse
     */
    public TicketDocument mutate(String ticketId, Consumer<TicketDocument> mutator) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            TicketDocument ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("Ticket bulunamadı: " + ticketId));

            mutator.accept(ticket);

            try {
                return ticketRepository.save(ticket);
            } catch (OptimisticLockingFailureException e) {
                logger.warn("Ticket üzerinde concurrent güncelleme çakışması, tekrar deneniyor ({}/{}). ticketId={}",
                        attempt, MAX_ATTEMPTS, ticketId);
            }
        }
        throw new IllegalStateException(
                "Ticket " + MAX_ATTEMPTS + " denemede güncellenemedi (yoğun concurrent çakışma): " + ticketId);
    }
}
