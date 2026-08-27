package com.skaanb.DejaView.repository;

import com.skaanb.DejaView.model.TicketDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends ElasticsearchRepository<TicketDocument, String> {

    // Belirli bir kullanıcının oluşturduğu ticket'ları getir
    List<TicketDocument> findByCreatedBy(String createdBy);

    List<TicketDocument> findByAiTagsContaining(String tag);

    // Aynı başlıkla (büyük/küçük harf duyarsız) daha önce açılmış ticket var mı kontrolü
    Optional<TicketDocument> findByTitleNormalized(String titleNormalized);
}
