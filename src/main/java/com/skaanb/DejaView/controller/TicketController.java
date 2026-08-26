package com.skaanb.DejaView.controller;

import com.skaanb.DejaView.dto.CreateTicketRequest;
import com.skaanb.DejaView.dto.TicketResponse;
import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable String id, Principal principal) {
        String username = (principal != null) ? principal.getName() : "anonymous_user";

        ticketService.deleteTicket(id, username);
        return ResponseEntity.noContent().build();
    }

    // Elasticsearch Arama
    @GetMapping("/search")
    public List<TicketResponse> searchTickets(@RequestParam("q") String query) {
        return ticketService.searchTickets(query);
    }
}