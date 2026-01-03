package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.controller.*;
import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the manual payment verification flow.
 *
 * <p>Supports recording transactions (made/pending), validating and issuing them by userId or
 * transactionId, and performing check-in operations that operate on groups.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManualTransactionService {

    private final TicketRepository ticketRepository;
    private final TicketIssuanceService ticketIssuanceService;
    private final TicketCheckInService ticketCheckInService;
    private final AuditoriumService auditoriumService;

    /**
     * Record a manual transaction (transaction made).
     *
     * @param req incoming request
     * @return result containing userId and ticket count
     */
    @Transactional
    public ManualTransactionResult recordTransaction(ManualTransactionController.RecordTransactionRequest req) {
        return recordTransactionInternal(req, TicketStatus.TRANSACTION_MADE);
    }

    /**
     * Record a manual transaction where payment is pending (cash pay at venue).
     *
     * @param req incoming request
     * @return result with userId and ticket count
     */
    @Transactional
    public ManualTransactionResult recordPendingTransaction(ManualTransactionController.RecordTransactionRequest req) {
        return recordTransactionInternal(req, TicketStatus.TRANSACTION_PENDING);
    }

    private ManualTransactionResult recordTransactionInternal(ManualTransactionController.RecordTransactionRequest req, TicketStatus status) {
        if (req.effectiveTicketCount() <= 0) {
            throw new IllegalArgumentException("ticketCount must be >= 1");
        }

        if (req.ticketAmount() == null) {
            throw new IllegalArgumentException("ticketAmount is required");
        }

        Optional<Ticket> existingForTxn = req.transactionId() != null
                ? ticketRepository.findFirstByTransactionId(req.transactionId())
                : Optional.empty();

        String userId = existingForTxn
                .map(Ticket::getUserId)
                .filter(id -> id != null && !id.isBlank())
                .orElseGet(() -> shortUuid());

        String normalizedPhone = normalizePhoneLast10(req.phoneNumber());

        Ticket purchase = new Ticket();
        purchase.setShowName(req.showName());
        purchase.setShowId(req.showId());
        purchase.setFullName(req.fullName());
        purchase.setEmail(req.email());
        purchase.setPhoneNumber(normalizedPhone);
        purchase.setUserId(userId);
        purchase.setTransactionId(req.transactionId() == null ? null : req.transactionId().trim());
        purchase.setTicketAmount(req.ticketAmount());
        purchase.setStatus(status);
        purchase.setTicketCount(req.effectiveTicketCount());

        int count = purchase.getTicketCount();
        List<Ticket> saved = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Ticket t = new Ticket();
            t.setShowName(purchase.getShowName());
            t.setShowId(purchase.getShowId());
            t.setFullName(purchase.getFullName());
            t.setEmail(purchase.getEmail());
            t.setPhoneNumber(normalizedPhone);
            t.setUserId(userId);
            t.setTransactionId(purchase.getTransactionId());
            t.setTicketAmount(purchase.getTicketAmount());
            t.setStatus(status);
            t.setTicketCount(count);
            saved.add(ticketRepository.save(t));
        }

        // Update auditorium seat counts (booked seats) for this show.
        auditoriumService.recalcByShowIdIfPresent(purchase.getShowId());

        log.info("event=manual_transaction_recorded userId={} ticketCount={} transactionId={} status={}", userId, saved.size(), req.transactionId(), status);

        return new ManualTransactionResult(userId, saved.size());
    }

    /**
     * Validate all pending/manual transactions for the given userId and mark them as ISSUED.
     *
     * @param userId user id
     * @return list of tickets after issuing
     */
    @Transactional
    public List<Ticket> validateTransactionAndIssueTickets(String userId) {
        List<Ticket> tickets = ticketRepository.findByUserId(userId);
        if (tickets.isEmpty()) {
            throw new IllegalArgumentException("No tickets found for userId=" + userId);
        }

        boolean anyPending = tickets.stream().anyMatch(t ->
                t.getStatus() == TicketStatus.TRANSACTION_MADE || t.getStatus() == TicketStatus.TRANSACTION_PENDING);
        if (!anyPending) {
            log.info("event=manual_transaction_validate_noop userId={} reason=no_pending", userId);
            return tickets;
        }

        for (Ticket t : tickets) {
            if (t.getStatus() == TicketStatus.TRANSACTION_MADE || t.getStatus() == TicketStatus.TRANSACTION_PENDING) {
                t.setStatus(TicketStatus.ISSUED);
                ticketRepository.save(t);
            }
        }

        // Update auditorium seat counts (confirmed seats).
        auditoriumService.recalcByShowIdIfPresent(tickets.get(0).getShowId());

        List<Ticket> issued = ticketRepository.findByUserId(userId);
        ticketIssuanceService.publishPurchaseIssuedEvent(issued);

        log.warn("event=manual_transaction_validated userId={} issuedCount={} ", userId, issued.size());
        return issued;
    }

    /**
     * Validate and issue all tickets for the given transaction id.
     *
     * @param transactionId transaction id
     * @return issued tickets
     */
    @Transactional
    public List<Ticket> validateTransactionAndIssueTicketsByTransactionId(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId required");
        }

        List<Ticket> tickets = ticketRepository.findByTransactionId(transactionId.trim());
        if (tickets.isEmpty()) {
            throw new IllegalArgumentException("No tickets found for transactionId=" + transactionId);
        }

        boolean anyPending = tickets.stream().anyMatch(t ->
                t.getStatus() == TicketStatus.TRANSACTION_MADE || t.getStatus() == TicketStatus.TRANSACTION_PENDING);
        if (!anyPending) {
            return tickets;
        }

        for (Ticket t : tickets) {
            if (t.getStatus() == TicketStatus.TRANSACTION_MADE || t.getStatus() == TicketStatus.TRANSACTION_PENDING) {
                t.setStatus(TicketStatus.ISSUED);
                ticketRepository.save(t);
            }
        }

        auditoriumService.recalcByShowIdIfPresent(tickets.get(0).getShowId());

        List<Ticket> issued = ticketRepository.findByTransactionId(transactionId.trim());
        ticketIssuanceService.publishPurchaseIssuedEvent(issued);
        return issued;
    }

    /**
     * Check-in all tickets belonging to a transaction id.
     *
     * @param transactionId transaction id
     * @return number of tickets updated to USED
     */
    @Transactional
    public int checkInByTransactionId(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId required");
        }
        List<Ticket> tickets = ticketRepository.findByTransactionId(transactionId.trim());
        if (tickets.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (Ticket t : tickets) {
            if (t.getQrCodeId() == null || t.getQrCodeId().isBlank()) continue;
            Ticket saved = ticketCheckInService.checkInByBarcode(t.getQrCodeId()).orElse(null);
            if (saved != null && saved.getStatus() == TicketStatus.USED) updated++;
        }

        if (!tickets.isEmpty()) {
            auditoriumService.recalcByShowIdIfPresent(tickets.get(0).getShowId());
        }
        return updated;
    }

    /**
     * Check-in all tickets for a user id.
     *
     * @param userId user id
     * @return number of tickets updated
     */
    @Transactional
    public int checkInByUserId(String userId) {
        List<Ticket> tickets = ticketRepository.findByUserId(userId);
        if (tickets.isEmpty()) {
            return 0;
        }

        int updated = 0;
        for (Ticket t : tickets) {
            if (t.getQrCodeId() == null || t.getQrCodeId().isBlank()) continue;
            Ticket saved = ticketCheckInService.checkInByBarcode(t.getQrCodeId()).orElse(null);
            if (saved != null && saved.getStatus() == TicketStatus.USED) updated++;
        }

        if (!tickets.isEmpty()) {
            auditoriumService.recalcByShowIdIfPresent(tickets.get(0).getShowId());
        }
        return updated;
    }

    /**
     * Normalize phone number keeping only last 10 digits.
     *
     * @param input raw phone input
     * @return normalized 10-digit phone or null
     */
    private static String normalizePhoneLast10(String input) {
        if (input == null) return null;
        String digits = input.replaceAll("\\D", "");
        if (digits.length() <= 10) return digits;
        return digits.substring(digits.length() - 10);
    }

    /**
     * Generate a short uppercase 8-char id used as fallback userId.
     *
     * @return 8-char uppercase id
     */
    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    public record ManualTransactionResult(String userId, int ticketCount) {}
}
