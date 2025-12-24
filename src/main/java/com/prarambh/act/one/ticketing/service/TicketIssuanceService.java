package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for issuing tickets.
 *
 * <p>Centralizes persistence + side effects (like emails) away from the controller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketIssuanceService {

    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Issues one purchase request which may contain multiple tickets.
     *
     * @param purchaseTicket a Ticket containing the customer/show fields + ticketCount
     * @return list of persisted ticket rows (size == ticketCount)
     */
    @Transactional
    public List<Ticket> issueTickets(Ticket purchaseTicket) {
        int count = purchaseTicket.getTicketCount();
        if (count <= 0) {
            count = 1;
        }

        List<Ticket> savedTickets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Ticket t = new Ticket();
            t.setShowName(purchaseTicket.getShowName());
            t.setShowId(purchaseTicket.getShowId());
            t.setFullName(purchaseTicket.getFullName());
            t.setEmail(purchaseTicket.getEmail());
            t.setPhoneNumber(purchaseTicket.getPhoneNumber());
            t.setStatus(purchaseTicket.getStatus());
            t.setTicketCount(count);

            Ticket saved = ticketRepository.save(t);
            log.debug("event=ticket_issued_persisted ticketId={} purchaseCount={} index={}", saved.getTicketId(), count, i + 1);
            // keep per-ticket event for backwards compatibility / other listeners
            eventPublisher.publishEvent(new TicketIssuedEvent(saved));
            savedTickets.add(saved);
        }

        // NEW: purchase-level event (one per API request)
        eventPublisher.publishEvent(new TicketPurchaseIssuedEvent(List.copyOf(savedTickets)));

        return savedTickets;
    }

    /**
     * Backwards-compatible single-ticket issuance.
     */
    @Transactional
    public Ticket issueTicket(Ticket ticket) {
        ticket.setTicketCount(Math.max(1, ticket.getTicketCount()));
        return issueTickets(ticket).get(0);
    }
}
