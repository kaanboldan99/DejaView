package com.skaanb.DejaView.repository;

import com.skaanb.DejaView.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Belirli bir kullanıcıya ait ticket'ları getir
    List<Ticket> findByUserId(Long userId);

    // Başlığa göre arama (case-insensitive)
    List<Ticket> findByTitleContainingIgnoreCase(String title);

    // Belirli bir etiketi içeren ticket'lar
    List<Ticket> findByTagsContaining(String tag);
}
