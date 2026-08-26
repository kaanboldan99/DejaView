package com.skaanb.DejaView.service;

import com.skaanb.DejaView.dto.CreateTicketRequest;
import com.skaanb.DejaView.dto.TicketResponse;
import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;

    @Autowired
    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    // GlobalExceptionHandler için yapay zekasız kayıt metodu
    public TicketDocument saveTicketLog(String errorMessage, String stackTrace, String serviceName) {
        TicketDocument ticket = new TicketDocument();
        ticket.setErrorMessage(errorMessage);
        ticket.setStackTrace(stackTrace);
        ticket.setServiceName(serviceName);
        ticket.setCreatedAt(Instant.now());

        ticket.setAiGeneratedDescription("AI analizi devre dışı bırakıldı.");
        ticket.setAiTags(List.of("Log"));
        ticket.setSolution("Lokal log kayıtları incelenmelidir.");
        ticket.setCreatedBy("system");

        return ticketRepository.save(ticket);
    }

    // Controller katmanından gelen manuel bilet oluşturma isteği
    public TicketResponse createTicket(CreateTicketRequest request, String username) {
        TicketDocument ticket = new TicketDocument();
        ticket.setErrorMessage(request.getDescription() != null ? request.getDescription() : "Manuel Kayıt");
        ticket.setStackTrace("Kullanıcı tarafından manuel oluşturuldu. Oluşturan: " + username);
        ticket.setServiceName("TicketController");
        ticket.setCreatedAt(Instant.now());

        ticket.setAiGeneratedDescription("Manuel bilet kaydı.");
        ticket.setAiTags(request.getTags() != null ? request.getTags() : List.of("Manual"));
        ticket.setSolution("Çözüm yolu henüz belirtilmedi.");
        ticket.setCreatedBy(username);

        TicketDocument saved = ticketRepository.save(ticket);
        return TicketResponse.fromTicket(saved);
    }

    public Optional<TicketDocument> getTicketById(String id) {
        return ticketRepository.findById(id);
    }

    public List<TicketDocument> getAllTickets() {
        return StreamSupport.stream(ticketRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());
    }

    public void deleteTicket(String id, String username) {
        TicketDocument ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket bulunamadı"));

        if (!username.equals(ticket.getCreatedBy())) {
            throw new RuntimeException("Bu ticket'ı silme yetkiniz yok");
        }

        ticketRepository.deleteById(id);
    }

    // query parametresine göre errorMessage, serviceName ve aiTags alanlarında
    // büyük/küçük harf duyarsız arama yapar
    public List<TicketResponse> searchTickets(String query) {
        if (query == null || query.isBlank()) {
            return StreamSupport.stream(ticketRepository.findAll().spliterator(), false)
                    .map(TicketResponse::fromTicket)
                    .collect(Collectors.toList());
        }

        String lowerQuery = query.toLowerCase();

        return StreamSupport.stream(ticketRepository.findAll().spliterator(), false)
                .filter(ticket ->
                        containsIgnoreCase(ticket.getErrorMessage(), lowerQuery)
                                || containsIgnoreCase(ticket.getServiceName(), lowerQuery)
                                || (ticket.getAiTags() != null && ticket.getAiTags().stream()
                                .anyMatch(tag -> containsIgnoreCase(tag, lowerQuery)))
                )
                .map(TicketResponse::fromTicket)
                .collect(Collectors.toList());
    }

    private boolean containsIgnoreCase(String source, String lowerQuery) {
        return source != null && source.toLowerCase().contains(lowerQuery);
    }
}