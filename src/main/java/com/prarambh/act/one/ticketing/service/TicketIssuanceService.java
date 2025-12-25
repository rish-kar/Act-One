package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
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
    private final CustomerIdService customerIdService;

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

        // Allocate a unique customerId for this purchase
        Integer providedCustomerId = purchaseTicket.getCustomerId();
        String providedTransactionId = purchaseTicket.getTransactionId();
        int customerId;
        if (providedCustomerId != null) {
            customerId = providedCustomerId;
        } else if (providedTransactionId != null) {
            customerId = ticketRepository.findFirstByTransactionId(providedTransactionId)
                    .map(Ticket::getCustomerId)
                    .filter(id -> id != null)
                    .orElseGet(customerIdService::allocateUniqueCustomerId);
        } else {
            customerId = customerIdService.allocateUniqueCustomerId();
        }

        String transactionId = providedTransactionId;
        if (transactionId == null || transactionId.isBlank()) {
            transactionId = "TXN-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        java.math.BigDecimal amount = purchaseTicket.getTicketAmount();
        if (amount == null) {
            throw new IllegalArgumentException("ticketAmount is required");
        }

        List<Ticket> savedTickets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Ticket t = new Ticket();
            t.setShowName(purchaseTicket.getShowName());
            t.setShowId(purchaseTicket.getShowId());
            t.setFullName(purchaseTicket.getFullName());
            t.setEmail(purchaseTicket.getEmail());
            t.setPhoneNumber(purchaseTicket.getPhoneNumber());
            // Set status to TRANSACTION_MADE - tickets need manual approval to become ISSUED
            t.setStatus(TicketStatus.TRANSACTION_MADE);
            t.setTicketCount(count);
            t.setCustomerId(customerId);
            t.setTransactionId(transactionId);
            t.setTicketAmount(amount);

            Ticket saved = ticketRepository.save(t);
            log.debug("event=ticket_recorded ticketId={} purchaseCount={} index={} customerId={}", saved.getTicketId(), count, i + 1, customerId);
            savedTickets.add(saved);
        }

        log.info("event=tickets_recorded customerId={} ticketCount={} status=TRANSACTION_MADE", customerId, savedTickets.size());

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

    /**
     * Publishes the purchase-issued event for an already-persisted list of tickets.
     *
     * <p>Used for manual transaction validation flow, where tickets are created as TRANSACTION_MADE
     * and later switched to ISSUED without creating new rows.
     */
    public void publishPurchaseIssuedEvent(List<Ticket> persistedTickets) {
        if (persistedTickets == null || persistedTickets.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new TicketPurchaseIssuedEvent(List.copyOf(persistedTickets)));
    }
}
