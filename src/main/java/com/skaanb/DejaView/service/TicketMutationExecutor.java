package com.skaanb.DejaView.service;

import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.elasticsearch.VersionConflictException;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Bir kayıt üzerinde "oku -> değiştir -> kaydet" işlemini eşzamanlı
 * güncellemelere karşı güvenli şekilde yürüten bileşen.
 *
 * Nasıl çalışır: Tek Sorumluluk ilkesi gereği bu sınıfın tek işi çakışma
 * yönetimidir. RabbitMQ dinleyicisi birden fazla thread ile çalıştığında
 * (bkz. {@code spring.rabbitmq.listener.simple.concurrency}) ya da bir
 * kullanıcı kayıt açarken aynı anda başka bir görülme/AI sonucu aynı dokümana
 * yazmaya çalıştığında, {@link TicketDocument} üzerindeki versiyon alanı
 * sayesinde eski versiyonla yapılan {@code save()} çakışma fırlatır. Bu sınıf
 * böyle bir çakışmada dokümanı YENİDEN OKUYUP değişikliği tekrar uygular,
 * yani "lost update" oluşmasını engeller.
 *
 * ÖNEMLİ: Spring Data Elasticsearch'ün versiyon desteği (external versioning)
 * JPA'nın aksine versiyonu otomatik ARTIRMAZ — {@code save()} her zaman nesne
 * üzerindeki mevcut versiyon değerini "bu değere eşit veya büyükse reddet"
 * olarak gönderir. Bu yüzden her kayıttan önce versiyon burada elle artırılır;
 * aksi halde ikinci {@code save()} her zaman çakışmayla başarısız olurdu.
 */
@Component
public class TicketMutationExecutor {

    private static final Logger logger = LoggerFactory.getLogger(TicketMutationExecutor.class);

    /** Çakışma hâlinde kaç kez yeniden denenecek. */
    private static final int MAX_ATTEMPTS = 5;

    /** Kaydı okuyup yazan depo. */
    private final TicketRepository ticketRepository;

    /**
     * @param ticketRepository Elasticsearch kayıt deposu
     */
    public TicketMutationExecutor(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * Kaydı okur, verilen değişikliği uygular ve kaydeder; çakışmada tekrar dener.
     *
     * Nasıl çalışır: her denemede doküman EN GÜNCEL hâliyle yeniden okunur,
     * {@code mutator} ona uygulanır, versiyon bir artırılır ve kaydedilir.
     * Çakışma olursa döngü baştan başlar — yani değişiklik, araya giren diğer
     * yazmanın SONUCU üzerine uygulanır, onu ezmez.
     *
     * Bu yüzden {@code mutator} yan etkisiz ve tekrar çalıştırılabilir olmalıdır:
     * birden fazla kez çağrılabilir. Örneğin "sayacı bir artır" güvenlidir,
     * ama dışarıya e-posta göndermek değildir.
     *
     * @param ticketId değiştirilecek kaydın doküman kimliği
     * @param mutator  doküman üzerinde yapılacak değişiklik; çakışma hâlinde
     *                 birden fazla kez çağrılabilir
     * @return kaydedilmiş, güncel doküman
     * @throws IllegalArgumentException kayıt bulunamazsa
     * @throws IllegalStateException    {@value #MAX_ATTEMPTS} denemede de çakışma çözülemezse
     */
    public TicketDocument mutate(String ticketId, Consumer<TicketDocument> mutator) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            TicketDocument ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new IllegalArgumentException("Ticket bulunamadı: " + ticketId));

            mutator.accept(ticket);

            /*
             * External versioning: Elasticsearch yalnızca verilen versiyon, saklanan
             * versiyondan KESİN OLARAK büyükse yazmayı kabul ediyor. findById ile
             * okunan versiyon saklanan değerin ta kendisi olduğu için elle artırmak şart.
             */
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
