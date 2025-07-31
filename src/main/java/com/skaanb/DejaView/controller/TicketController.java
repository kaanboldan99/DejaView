package com.skaanb.DejaView.controller;

import com.skaanb.DejaView.dto.CreateTicketRequest;
import com.skaanb.DejaView.dto.TicketResponse;
import com.skaanb.DejaView.model.Ticket;
import com.skaanb.DejaView.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    @Autowired
    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    // Tüm Ticket'lar
    @GetMapping
    public List<TicketResponse> getAllTickets() {
        return ticketService.getAllTickets()
                .stream()
                .map(TicketResponse::fromTicket)
                .collect(Collectors.toList());
    }

    // Tekil Ticket
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicketById(@PathVariable Long id) {
        Ticket ticket = ticketService.getTicketById(id)
                .orElseThrow(() -> new RuntimeException("Ticket bulunamadı")); // veya custom exception
        TicketResponse response = TicketResponse.fromTicket(ticket);
        return ResponseEntity.ok(response);
    }

    // Yeni Ticket Oluşutur
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@RequestBody CreateTicketRequest request, Principal principal) {
        TicketResponse created = ticketService.createTicket(request, principal.getName());
        return ResponseEntity.ok(created);
    }

    // Ticket Sil
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id, Principal principal) {
        ticketService.deleteTicket(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    // Elasticsearch Arama
    @GetMapping("/search")
    public List<TicketResponse> searchTickets(@RequestParam("q") String query) {
        return ticketService.searchTickets(query);
    }
}