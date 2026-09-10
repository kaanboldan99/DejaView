package com.skaanb.DejaView.repository;

import com.skaanb.DejaView.model.TicketDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Elasticsearch'teki {@code dejaview_tickets} indeksine erişim.
 *
 * Nasıl çalışır: {@link ElasticsearchRepository} temel CRUD işlemlerini hazır
 * verir; aşağıdaki metotların gövdesi yoktur — Spring Data metot ADINDAN
 * sorguyu türetir. Kayıtların birincil deposu ilişkisel veritabanı değil
 * Elasticsearch'tür, bu yüzden doküman kimliği {@code String}'tir ve başlıktan
 * deterministik olarak üretilir
 * (bkz. {@link com.skaanb.DejaView.service.TicketService}).
 *
 * Eşzamanlı güncellemeler doğrudan {@code save()} ile değil
 * {@link com.skaanb.DejaView.service.TicketMutationExecutor} üzerinden
 * yapılmalıdır; aksi halde iki thread birbirinin yazdığını sessizce ezebilir.
 */
@Repository
public interface TicketRepository extends ElasticsearchRepository<TicketDocument, String> {

    /**
     * Belirli bir kullanıcının oluşturduğu kayıtları getirir.
     *
     * Nasıl çalışır: {@code createdBy} alanı Keyword tipinde olduğu için
     * eşleşme birebirdir (büyük/küçük harf duyarlı).
     *
     * @param createdBy kaydı oluşturan kullanıcı adı
     * @return o kullanıcıya ait kayıtlar; yoksa boş liste
     */
    List<TicketDocument> findByCreatedBy(String createdBy);

    /**
     * Verilen etikete sahip kayıtları getirir.
     *
     * Nasıl çalışır: {@code aiTags} bir Keyword dizisi; sorgu dizinin
     * elemanlarından biriyle birebir eşleşme arar. Etiketler zaten küçük harfe
     * normalize edilerek yazıldığı için parametre de küçük harf verilmelidir
     * (bkz. {@link com.skaanb.DejaView.service.OpenAiService}).
     *
     * @param tag aranan etiket (küçük harfli, tekil teknik terim)
     * @return etiketi taşıyan kayıtlar; yoksa boş liste
     */
    List<TicketDocument> findByAiTagsContaining(String tag);

    /**
     * Aynı başlıkla daha önce açılmış kayıt olup olmadığını kontrol eder.
     *
     * Nasıl çalışır: karşılaştırma ham başlıkla değil, başlığın kırpılmış ve
     * küçük harfe çevrilmiş hâliyle yapılır; böylece "Timeout" ile "timeout "
     * aynı kayda düşer.
     *
     * @param titleNormalized normalize edilmiş başlık (kırpılmış + küçük harf)
     * @return eşleşen kayıt; yoksa boş {@link Optional}
     */
    Optional<TicketDocument> findByTitleNormalized(String titleNormalized);
}
