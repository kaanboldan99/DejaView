package com.skaanb.DejaView.service;

import com.skaanb.DejaView.model.Ticket;
import com.skaanb.DejaView.model.User;
import com.skaanb.DejaView.repository.TicketRepository;
import com.skaanb.DejaView.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public TicketService(TicketRepository ticketRepository, UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    public Ticket createTicket(Ticket ticket, Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            throw new RuntimeException("User not found with id: " + userId);
        }

        ticket.setUser(userOptional.get());
        return ticketRepository.save(ticket);
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

    public void deleteTicket(Long id) {
        ticketRepository.deleteById(id);
    }

    public List<Ticket> searchByTitle(String keyword) {
        return ticketRepository.findByTitleContainingIgnoreCase(keyword);
    }

    public List<Ticket> searchByTag(String tag) {
        return ticketRepository.findByTagsContaining(tag);
    }
}
