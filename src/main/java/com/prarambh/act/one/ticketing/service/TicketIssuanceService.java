package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.repository.UserRepository;
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
    private final UserIdService userIdService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final AuditoriumService auditoriumService;

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

        // Allocate a unique userId for this purchase
        String providedUserId = purchaseTicket.getUserId();
        String providedTransactionId = purchaseTicket.getTransactionId();
        String userId;
        if (providedUserId != null && !providedUserId.isBlank()) {
            userId = providedUserId.trim();
        } else if (providedTransactionId != null && !providedTransactionId.isBlank()) {
            userId = ticketRepository.findFirstByTransactionId(providedTransactionId)
                    .map(Ticket::getUserId)
                    .filter(id -> id != null && !id.isBlank())
                    .orElseGet(userIdService::allocateUniqueUserId);
        } else {
            userId = userIdService.allocateUniqueUserId();
        }

        String transactionId = providedTransactionId;
        if (transactionId == null || transactionId.isBlank()) {
            transactionId = "TXN-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        java.math.BigDecimal amount = purchaseTicket.getTicketAmount();
        if (amount == null) {
            throw new IllegalArgumentException("ticketAmount is required");
        }

        // prepare fields for user lookup
        String fullName = purchaseTicket.getFullName();
        String email = purchaseTicket.getEmail();
        String phone = purchaseTicket.getPhoneNumber();
        String last10 = null;
        if (phone != null) {
            String digits = phone.replaceAll("\\D", "");
            if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
            last10 = digits;
        }

        // NEW: if a user already exists in users table with same fullName+phone+email,
        // use that user's userId for the tickets. This ensures multiple purchases by the same
        // person keep the same userId.
        String normalizedEmail = email == null ? null : email.trim();
        String existingUserId = null;
        if (fullName != null && last10 != null && normalizedEmail != null) {
            var match = userRepository.findFirstByFullNameAndPhoneNumberAndEmail(fullName, last10, normalizedEmail);
            if (match != null) {
                existingUserId = match.getUserId();
                log.debug("event=found_existing_user_for_ticket userId={} fullName={} phone={} email={}", existingUserId, fullName, last10, normalizedEmail);
            }
        }

        List<Ticket> savedTickets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Ticket t = new Ticket();
            t.setShowName(purchaseTicket.getShowName());
            t.setShowId(purchaseTicket.getShowId());
            t.setFullName(fullName);
            t.setEmail(email);
            t.setPhoneNumber(last10 != null ? last10 : purchaseTicket.getPhoneNumber());
            // Set status to TRANSACTION_MADE - tickets need manual approval to become ISSUED
            t.setStatus(TicketStatus.TRANSACTION_MADE);
            t.setTicketCount(count);

            // Use existing user id if we found a match; otherwise use computed userId variable
            t.setUserId(existingUserId != null ? existingUserId : userId);
            t.setTransactionId(transactionId);
            t.setTicketAmount(amount);

            Ticket saved = ticketRepository.save(t);

            // Update auditorium seats for this show whenever we record a booked ticket.
            auditoriumService.recalcByShowIdIfPresent(saved.getShowId());

            // Ensure a corresponding User record exists for this userId.
            try {
                // pass the final assigned user id and normalized fields
                userService.ensureUserForTicket(t.getUserId(), fullName, last10 != null ? last10 : purchaseTicket.getPhoneNumber(), normalizedEmail);
            } catch (Exception e) {
                log.warn("event=ensure_user_failed userId={} reason={}", t.getUserId(), e.getMessage());
            }

            log.debug("event=ticket_recorded ticketId={} purchaseCount={} index={} userId={}", saved.getTicketId(), count, i + 1, t.getUserId());
            savedTickets.add(saved);
        }

        log.info("event=tickets_recorded userId={} ticketCount={} status=TRANSACTION_MADE", userId, savedTickets.size());

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
