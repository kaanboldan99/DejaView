package com.skaanb.DejaView.service;

import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketMutationExecutorTest {

    @Mock
    private TicketRepository ticketRepository;

    private TicketMutationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new TicketMutationExecutor(ticketRepository);
    }

    @Test
    void testMutate_Success_AppliesMutatorOnce() {
        // Given
        TicketDocument ticket = new TicketDocument();
        ticket.setId("ticket-1");
        ticket.setOccurrenceCount(1);
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(TicketDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        TicketDocument result = executor.mutate("ticket-1", t -> t.setOccurrenceCount(t.getOccurrenceCount() + 1));

        // Then
        assertEquals(2, result.getOccurrenceCount());
        verify(ticketRepository, times(1)).findById("ticket-1");
        verify(ticketRepository, times(1)).save(any(TicketDocument.class));
    }

    @Test
    void testMutate_ConflictThenSuccess_RetriesAndReappliesMutator() {
        // Given: ilk save çakışıyor (başka bir thread araya girdi), ikinci denemede
        // dokümanı yeniden okuyup mutator'ı tekrar uyguluyoruz.
        TicketDocument firstRead = new TicketDocument();
        firstRead.setId("ticket-1");
        firstRead.setOccurrenceCount(1);

        TicketDocument secondRead = new TicketDocument();
        secondRead.setId("ticket-1");
        secondRead.setOccurrenceCount(5); // başka bir thread bu arada güncellemiş

        when(ticketRepository.findById("ticket-1"))
                .thenReturn(Optional.of(firstRead))
                .thenReturn(Optional.of(secondRead));
        when(ticketRepository.save(any(TicketDocument.class)))
                .thenThrow(new OptimisticLockingFailureException("version conflict"))
                .thenAnswer(inv -> inv.getArgument(0));

        // When
        TicketDocument result = executor.mutate("ticket-1", t -> t.setOccurrenceCount(t.getOccurrenceCount() + 1));

        // Then: mutator en güncel (5'ten gelen) veriye uygulanmış olmalı, ilk okumadaki
        // eski veriye göre değil.
        assertEquals(6, result.getOccurrenceCount());
        verify(ticketRepository, times(2)).findById("ticket-1");
        verify(ticketRepository, times(2)).save(any(TicketDocument.class));
    }

    @Test
    void testMutate_PersistentConflict_ThrowsAfterMaxAttempts() {
        // Given: her denemede çakışma oluyor
        TicketDocument ticket = new TicketDocument();
        ticket.setId("ticket-1");
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(TicketDocument.class)))
                .thenThrow(new OptimisticLockingFailureException("version conflict"));

        // When & Then
        assertThrows(IllegalStateException.class, () -> executor.mutate("ticket-1", t -> {}));
    }

    @Test
    void testMutate_TicketNotFound_ThrowsIllegalArgumentException() {
        // Given
        when(ticketRepository.findById("missing")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> executor.mutate("missing", t -> {}));
        verify(ticketRepository, never()).save(any());
    }
}
