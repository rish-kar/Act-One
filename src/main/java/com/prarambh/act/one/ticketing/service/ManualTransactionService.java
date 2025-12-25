package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the manual payment verification flow:
 * 1) Record a transaction (create ticket rows in TRANSACTION_MADE)
 * 2) Admin validates the transaction and moves tickets to ISSUED (and triggers email/card generation)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManualTransactionService {

    private final TicketRepository ticketRepository;
    private final CustomerIdService customerIdService;
    private final TicketIssuanceService ticketIssuanceService;

    @Transactional
    public ManualTransactionResult recordTransaction(RecordTransactionRequest req) {
        if (req.ticketCount() <= 0) {
            throw new IllegalArgumentException("ticketCount must be >= 1");
        }
        if (req.transactionId() == null || req.transactionId().isBlank()) {
            throw new IllegalArgumentException("transactionId is required");
        }

        int customerId = customerIdService.allocateUniqueCustomerId();

        Ticket purchase = new Ticket();
        purchase.setShowName(req.showName());
        purchase.setShowId(req.showId());
        purchase.setFullName(req.fullName());
        purchase.setEmail(req.email());
        purchase.setPhoneNumber(req.phoneNumber());
        purchase.setCustomerId(customerId);
        purchase.setTransactionId(req.transactionId().trim());
        purchase.setStatus(TicketStatus.TRANSACTION_MADE);
        purchase.setTicketCount(req.ticketCount());

        // Create N ticket rows but DO NOT issue email/cards (status is not ISSUED).
        int count = purchase.getTicketCount();
        List<Ticket> saved = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Ticket t = new Ticket();
            t.setShowName(purchase.getShowName());
            t.setShowId(purchase.getShowId());
            t.setFullName(purchase.getFullName());
            t.setEmail(purchase.getEmail());
            t.setPhoneNumber(purchase.getPhoneNumber());
            t.setCustomerId(customerId);
            t.setTransactionId(purchase.getTransactionId());
            t.setStatus(TicketStatus.TRANSACTION_MADE);
            t.setTicketCount(count);
            saved.add(ticketRepository.save(t));
        }

        log.info("event=manual_transaction_recorded customerId={} ticketCount={} transactionId={}", customerId, saved.size(), req.transactionId());

        return new ManualTransactionResult(customerId, saved.size());
    }

    /**
     * Moves all tickets of a customer from TRANSACTION_MADE -> ISSUED.
     *
     * <p>This updates existing rows (keeps ticketId/barcodeId stable) then triggers the existing
     * purchase-issued email/card generation by publishing {@link TicketPurchaseIssuedEvent}.
     */
    @Transactional
    public List<Ticket> validateTransactionAndIssueTickets(int customerId) {
        List<Ticket> tickets = ticketRepository.findByCustomerId(customerId);
        if (tickets.isEmpty()) {
            throw new IllegalArgumentException("No tickets found for customerId=" + customerId);
        }

        boolean anyPending = tickets.stream().anyMatch(t -> t.getStatus() == TicketStatus.TRANSACTION_MADE);
        if (!anyPending) {
            log.info("event=manual_transaction_validate_noop customerId={} reason=no_pending", customerId);
            return tickets;
        }

        for (Ticket t : tickets) {
            if (t.getStatus() == TicketStatus.TRANSACTION_MADE) {
                t.setStatus(TicketStatus.ISSUED);
                ticketRepository.save(t);
            }
        }

        List<Ticket> issued = ticketRepository.findByCustomerId(customerId);
        ticketIssuanceService.publishPurchaseIssuedEvent(issued);

        log.warn("event=manual_transaction_validated customerId={} issuedCount={} ", customerId, issued.size());
        return issued;
    }

    public record RecordTransactionRequest(
            String showId,
            String showName,
            String fullName,
            String email,
            String phoneNumber,
            int ticketCount,
            String transactionId
    ) {}

    public record ManualTransactionResult(int customerId, int ticketCount) {}
}
