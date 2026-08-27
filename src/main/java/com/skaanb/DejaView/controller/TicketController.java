package com.skaanb.DejaView.controller;

import com.skaanb.DejaView.dto.CreateTicketRequest;
import com.skaanb.DejaView.dto.TicketResponse;
import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    @Autowired
    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    // Tüm Ticket'lar (Elasticsearch Document Yapısına Uyumlu)
    @GetMapping
    public List<TicketDocument> getAllTickets() {
        return ticketService.getAllTickets();
    }

    // Tekil Ticket (ID veri tipi Long yerine String olarak güncellendi)
    @GetMapping("/{id}")
    public ResponseEntity<TicketDocument> getTicketById(@PathVariable String id) {
        TicketDocument ticket = ticketService.getTicketById(id)
                .orElseThrow(() -> new RuntimeException("Ticket bulunamadı"));
        return ResponseEntity.ok(ticket);
    }

    // Yeni Ticket Oluştur
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@RequestBody CreateTicketRequest request, Principal principal) {
        // Lokal testlerde JWT token gönderilmediğinde NullPointerException (500 hatası)
        // fırlatmaması için güvenli bypass kontrolü:
        String username = (principal != null) ? principal.getName() : "anonymous_user";

        TicketResponse created = ticketService.createTicket(request, username);
        return ResponseEntity.ok(created);
    }

    // Ticket Sil (ID veri tipi Long yerine String olarak güncellendi)
    // ADMIN rolündeki kullanıcılar sahiplik kontrolüne takılmadan her ticket'ı silebilir.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable String id, Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : "anonymous_user";
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        ticketService.deleteTicket(id, username, isAdmin);
        return ResponseEntity.noContent().build();
    }

    // Admin: mevcut bir ticket için AI analizini yeniden tetikler (SecurityConfig'de ADMIN'e kısıtlı).
    @PostMapping("/{id}/resummarize")
    public ResponseEntity<TicketResponse> resummarizeTicket(@PathVariable String id) {
        return ResponseEntity.ok(ticketService.resummarizeTicket(id));
    }

    // Elasticsearch Arama
    @GetMapping("/search")
    public List<TicketResponse> searchTickets(@RequestParam("q") String query) {
        return ticketService.searchTickets(query);
    }
}