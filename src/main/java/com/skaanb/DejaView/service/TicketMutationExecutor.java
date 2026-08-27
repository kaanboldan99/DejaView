package com.skaanb.DejaView.service;

import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.elasticsearch.VersionConflictException;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

// Tek Sorumluluk: bir TicketDocument üzerinde "oku -> değiştir -> kaydet" işlemini
// concurrent güncellemelere karşı güvenli şekilde yapmak. RabbitMQ dinleyicisi birden
// fazla thread ile çalıştığında (bkz. application.properties: listener.simple.concurrency)
// veya bir kullanıcı ticket oluştururken aynı anda başka bir occurrence/AI sonucu aynı
// dokümana yazmaya çalışırsa, TicketDocument@Version sayesinde eski versiyonla yapılan
// save() çakışma fırlatır; bu sınıf böyle bir çakışmada dokümanı yeniden okuyup
// mutator'ı tekrar uygulayarak "lost update" oluşmasını engeller.
//
// ÖNEMLİ: Spring Data Elasticsearch'ün @Version desteği (external versioning) JPA'nın
// aksine version'ı otomatik ARTIRMAZ — save() her zaman entity üzerindeki mevcut version
// değerini "bu değere eşit veya büyükse reddet" olarak gönderir. Bu yüzden her kayıttan
// önce version'ı burada elle artırıyoruz; aksi halde ikinci save() her zaman
// VersionConflictException ile başarısız olur (version, okunanla aynı kalır).
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
     * güncelleme çakışması olursa (VersionConflictException / OptimisticLockingFailureException)
     * dokümanı en güncel haliyle yeniden okuyup mutator'ı tekrar uygular.
     *
     * @throws IllegalArgumentException ticket bulunamazsa
     * @throws IllegalStateException MAX_ATTEMPTS denemede de çakışma çözülemezse
     */
    public TicketDocument mutate(String ticketId, Consumer<TicketDocument> mutator) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            TicketDocument ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("Ticket bulunamadı: " + ticketId));

            mutator.accept(ticket);

            // external versioning: ES sadece verilen version, mevcut saklanan versiyondan
            // KESİN OLARAK büyükse yazmayı kabul ediyor. findById ile okunan versiyon
            // mevcut saklanan değerin ta kendisi olduğu için elle artırmak şart.
            Long currentVersion = ticket.getVersion();
            ticket.setVersion(currentVersion == null ? 1L : currentVersion + 1);

            try {
                return ticketRepository.save(ticket);
            } catch (VersionConflictException | OptimisticLockingFailureException e) {
                logger.warn("Ticket üzerinde concurrent güncelleme çakışması, tekrar deneniyor ({}/{}). ticketId={}",
                        attempt, MAX_ATTEMPTS, ticketId);
            }
        }
        throw new IllegalStateException(
                "Ticket " + MAX_ATTEMPTS + " denemede güncellenemedi (yoğun concurrent çakışma): " + ticketId);
    }
}
