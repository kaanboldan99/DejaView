package com.skaanb.DejaView.service;

import com.skaanb.DejaView.dto.TicketDocumentResponse;
import com.skaanb.DejaView.dto.CreateTicketRequest;
import com.skaanb.DejaView.dto.TicketResponse;
import com.skaanb.DejaView.model.Ticket;
import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.model.User;
import com.skaanb.DejaView.repository.TicketRepository;
import com.skaanb.DejaView.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;


import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ElasticsearchRestTemplate elasticsearchTemplate;
    private final OpenAiService openAiService;

    @Autowired
    public TicketService(
            TicketRepository ticketRepository,
            UserRepository userRepository,
            ElasticsearchRestTemplate elasticsearchTemplate,
            OpenAiService openAiService
    ) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.elasticsearchTemplate = elasticsearchTemplate;
        this.openAiService = openAiService;
    }

    public TicketResponse createTicket(CreateTicketRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı"));

        Ticket ticket = new Ticket();
        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setUser(user);

        String summary = openAiService.summarize(request.getDescription());
        ticket.setSummary(summary);

        Ticket saved = ticketRepository.save(ticket);
        return TicketResponse.fromTicket(saved);
    }

    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    public Optional<Ticket> getTicketById(Long id) {
        return ticketRepository.findById(id);
    }

    public List<Ticket> getTicketsByUserId(Long userId) {
        return ticketRepository.findByUserId(userId);
    }

    public void deleteTicket(Long id, String username) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket bulunamadı"));

        if (!ticket.getUser().getUsername().equals(username)) {
            throw new RuntimeException("Bu ticket'ı silmeye yetkiniz yok.");
        }

        ticketRepository.delete(ticket);
    }


    public List<Ticket> searchByTitle(String keyword) {
        return ticketRepository.findByTitleContainingIgnoreCase(keyword);
    }

    public List<Ticket> searchByTag(String tag) {
        return ticketRepository.findByTagsContaining(tag);
    }

    public List<TicketResponse> searchTickets(String query) {
        Query searchQuery = new NativeSearchQueryBuilder()
                .withQuery(multiMatchQuery(query, "title", "summary", "description"))
                .build();

        SearchHits<TicketDocument> hits = elasticsearchTemplate.search(searchQuery, TicketDocument.class);

        return hits.stream()
                .map(hit -> TicketDocumentResponse.fromDocument(hit.getContent()))
                .collect(Collectors.toList());
    }
}
