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

        String fullName = purchaseTicket.getFullName();
        String email = purchaseTicket.getEmail();
        String phone = purchaseTicket.getPhoneNumber();

        // UserId must be based ONLY on identity: Full Name + Email + Phone.
        // If even one character differs in any of those, a new userId must be allocated.
        // transactionId must NOT affect userId generation.
        String providedUserId = purchaseTicket.getUserId();

        String userId = userService.resolveOrCreateUserId(fullName, phone, email);

        // Backwards compatibility: if identity-based resolution returned null (shouldn't), fall back to provided userId.
        if (userId == null && providedUserId != null && !providedUserId.isBlank()) {
            userId = providedUserId.trim();
        }

        if (userId == null) {
            userId = userIdService.allocateUniqueUserId();
        }

        // transactionId stays whatever the caller provided; if missing generate one.
        String transactionId = purchaseTicket.getTransactionId();
        if (transactionId == null || transactionId.isBlank()) {
            transactionId = "TXN-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        java.math.BigDecimal amount = purchaseTicket.getTicketAmount();
        if (amount == null) {
            throw new IllegalArgumentException("ticketAmount is required");
        }

        // Normalize phone for ticket storage (last 10 digits)
        String last10 = null;
        if (phone != null) {
            String digits = phone.replaceAll("\\D", "");
            if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
            last10 = digits;
        }

        String normalizedEmail = email == null ? null : email.trim();

        List<Ticket> savedTickets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Ticket t = new Ticket();
            // If the caller provided an auditoriumId, keep it on the ticket so we can reference the auditorium later
            if (purchaseTicket.getAuditoriumId() != null && !purchaseTicket.getAuditoriumId().isBlank()) {
                t.setAuditoriumId(purchaseTicket.getAuditoriumId().trim());
            }

            // If showId or showName is missing, attempt to populate them from the auditorium
            if ((purchaseTicket.getShowId() == null || purchaseTicket.getShowId().isBlank()) || (purchaseTicket.getShowName() == null || purchaseTicket.getShowName().isBlank())) {
                String aid = purchaseTicket.getAuditoriumId();
                if (aid != null && !aid.isBlank()) {
                    auditoriumService.findById(aid.trim()).ifPresent(aud -> {
                        if (t.getShowId() == null || t.getShowId().isBlank()) t.setShowId(aud.getShowId());
                        if (t.getShowName() == null || t.getShowName().isBlank()) t.setShowName(aud.getShowName());
                    });
                }
            }
            // Fallback to values passed on the purchaseTicket
            t.setShowName(purchaseTicket.getShowName());
            if (t.getShowId() == null || t.getShowId().isBlank()) t.setShowId(purchaseTicket.getShowId());
            t.setFullName(fullName);
            t.setEmail(email);
            t.setPhoneNumber(last10 != null ? last10 : purchaseTicket.getPhoneNumber());
            t.setStatus(TicketStatus.TRANSACTION_MADE);
            t.setTicketCount(count);

            t.setUserId(userId);
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

