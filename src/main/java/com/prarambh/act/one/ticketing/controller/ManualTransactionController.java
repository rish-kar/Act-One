package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.ManualTransactionService;
import com.prarambh.act.one.ticketing.service.TicketCheckInService;
import com.prarambh.act.one.ticketing.service.TicketIssuanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Manual transaction verification flow.
 *
 * <p>1) Customer submits transactionId and details -> stored as TRANSACTION_MADE.
 * <p>2) Admin manually validates -> transition to ISSUED and trigger email/cards.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class ManualTransactionController {

    private final ManualTransactionService manualTransactionService;
    private final TicketRepository ticketRepository;
    private final TicketIssuanceService ticketIssuanceService;
    private final TicketCheckInService ticketCheckInService;

    @Value("${actone.admin.purge-password:}")
    private String adminPassword;

    /**
     * Record a successful transaction (manual verification pending).
     *
     * <p>Creates ticket rows in TRANSACTION_MADE and assigns a 4-digit customerId.
     */
    @PostMapping("/record")
    public ResponseEntity<?> recordTransaction(@Valid @RequestBody RecordTransactionRequest req) {
        ManualTransactionService.ManualTransactionResult result = manualTransactionService.recordTransaction(req);
        return ResponseEntity.ok(Map.of(
                "userId", result.userId(),
                "ticketCount", result.ticketCount(),
                "status", TicketStatus.TRANSACTION_MADE.name()
        ));
    }

    /**
     * Record a cash transaction that will be paid later (manual verification/pending payment).
     */
    @PostMapping("/record-pending")
    public ResponseEntity<?> recordPendingTransaction(@Valid @RequestBody RecordTransactionRequest req) {
        ManualTransactionService.ManualTransactionResult result = manualTransactionService.recordPendingTransaction(req);
        return ResponseEntity.ok(Map.of(
                "userId", result.userId(),
                "ticketCount", result.ticketCount(),
                "status", TicketStatus.TRANSACTION_PENDING.name()
        ));
    }

    /**
     * Admin endpoint: validate transaction and issue tickets for a customer.
     */
    @PostMapping("/{userId}/validate")
    public ResponseEntity<?> validateAndIssue(@PathVariable String userId) {
        List<Ticket> issued = manualTransactionService.validateTransactionAndIssueTickets(userId);
        if (issued.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found", "userId", userId));
        }
        Ticket first = issued.get(0);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "fullName", first.getFullName(),
                "phoneNumber", first.getPhoneNumber(),
                "ticketCount", issued.size(),
                "ticketAmount", first.getTicketAmount(),
                "transactionId", first.getTransactionId(),
                "ticketStatus", TicketStatus.ISSUED.name()
        ));
    }

    /**
     * Retrieve transaction details by phone number.
     */
    @GetMapping("/by-phone")
    public ResponseEntity<?> getByPhone(@RequestParam String phoneNumber) {
        String digits = normalizePhoneDigits(phoneNumber);
        if (digits == null || digits.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "phoneNumber is required"));
        }

        // Partial search support:
        // - if user enters <= 10 digits, match anywhere to support partial lookups
        // - if user enters > 10 digits, match on last 10 (common use case)
        List<Ticket> tickets;
        if (digits.length() > 10) {
            String last10 = digits.substring(digits.length() - 10);
            tickets = ticketRepository.findByPhoneNumberEndingWith(last10);
        } else {
            tickets = ticketRepository.findByPhoneNumberContainingIgnoreCase(digits);
        }
        return summarizeTickets(tickets);
    }

    private static String normalizePhoneDigits(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("\\D", "");
    }

    /**
     * Retrieve transaction details by customer name.
     */
    @GetMapping("/by-name")
    public ResponseEntity<?> getByName(@RequestParam String fullName) {
        String q = fullName == null ? "" : fullName.trim();
        if (q.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "fullName is required"));
        }
        // Partial search support (contains, case-insensitive)
        List<Ticket> tickets = ticketRepository.findByFullNameContainingIgnoreCase(q);
        return summarizeTickets(tickets);
    }

    private ResponseEntity<?> summarizeTickets(List<Ticket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No matching records"));
        }
        Ticket first = tickets.get(0);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", first.getUserId());
        payload.put("fullName", first.getFullName());
        payload.put("phoneNumber", first.getPhoneNumber());
        payload.put("ticketCount", tickets.size());
        payload.put("ticketAmount", first.getTicketAmount() != null ? first.getTicketAmount().toPlainString() : null);
        payload.put("transactionId", first.getTransactionId());
        payload.put("ticketStatus", first.getStatus() != null ? first.getStatus().name() : null);
        payload.put("ticketNumbers", tickets.stream().map(t -> t.getTicketId() != null ? t.getTicketId().toString() : null).toList());
        return ResponseEntity.ok(payload);
    }

    /**
     * Admin endpoint: issue tickets by customer ID.
     */
    @PostMapping("/{userId}/issue")
    public ResponseEntity<?> issueByUserId(@PathVariable String userId) {
        List<Ticket> issued = manualTransactionService.validateTransactionAndIssueTickets(userId);
        if (issued.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found", "userId", userId));
        }
        return ResponseEntity.ok(Map.of(
                "message", "Ticket is confirmed",
                "userId", userId,
                "issuedCount", issued.size()
        ));
    }

    /**
     * Admin endpoint: check-in by customer ID.
     */
    @PostMapping("/{userId}/checkin")
    public ResponseEntity<?> checkInByUserId(@PathVariable String userId) {
        int used = manualTransactionService.checkInByUserId(userId);
        if (used == 0) {
            return ResponseEntity.status(404).body(Map.of("message", "User not found", "userId", userId));
        }
        return ResponseEntity.ok(Map.of(
                "message", "Check-in successful",
                "userId", userId,
                "usedCount", used
        ));
    }

    /**
     * Admin endpoint: validate transaction and issue tickets by transaction ID.
     */
    @PostMapping("/by-transaction/{transactionId}/validate")
    public ResponseEntity<?> validateAndIssueByTransaction(
            @PathVariable String transactionId,
            @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword
    ) {
        if (!isAdminPasswordValid(headerPassword)) return ResponseEntity.status(403).body(Map.of("message","Invalid admin password"));

        try {
            List<Ticket> issued = manualTransactionService.validateTransactionAndIssueTicketsByTransactionId(transactionId);
            return ResponseEntity.ok(Map.of("transactionId", transactionId, "issuedCount", issued.size()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage(), "transactionId", transactionId));
        }
    }

    /**
     * Admin endpoint: check-in by transaction ID. This will mark all tickets with the given
     * transactionId as USED (if they are ISSUED).
     */
    @PostMapping("/by-transaction/{transactionId}/checkin")
    public ResponseEntity<?> checkInByTransaction(
            @PathVariable String transactionId,
            @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword
    ) {
        if (!isAdminPasswordValid(headerPassword)) return ResponseEntity.status(403).body(Map.of("message","Invalid admin password"));

        try {
            int checkedIn = manualTransactionService.checkInByTransactionId(transactionId);
            if (checkedIn == 0) {
                return ResponseEntity.status(404).body(Map.of("message", "Transaction not found", "transactionId", transactionId));
            }
            return ResponseEntity.ok(Map.of("transactionId", transactionId, "checkedInCount", checkedIn));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Retrieve transaction (grouped by transactionId) details for a given transactionId.
     * Returns user details + transaction rows grouped by transactionId. Admin header required.
     */
    @GetMapping("/by-transaction/{transactionId}")
    public ResponseEntity<?> getTransactionDetailsByTransactionId(@PathVariable String transactionId,
                                                                   @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword) {
        if (!isAdminPasswordValid(headerPassword)) return ResponseEntity.status(403).body(Map.of("message","Invalid admin password"));

        List<Ticket> tickets = ticketRepository.findByTransactionId(transactionId);
        if (tickets == null || tickets.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message","Transaction not found","transactionId", transactionId));
        }

        Ticket first = tickets.get(0);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("transactionId", transactionId);
        m.put("userId", first.getUserId());
        m.put("fullName", first.getFullName());
        m.put("phoneNumber", first.getPhoneNumber());
        m.put("email", first.getEmail());
        m.put("ticketAmount", first.getTicketAmount() != null ? first.getTicketAmount().toPlainString() : null);
        m.put("ticketStatus", first.getStatus() != null ? first.getStatus().name() : null);
        m.put("ticketCount", tickets.size());
        m.put("ticketIds", tickets.stream().map(t->t.getTicketId().toString()).toList());

        return ResponseEntity.ok(m);
    }

    /**
     * Retrieve all transactions for a specific userId.
     * Groups tickets by transactionId and returns an array of transaction objects.
     */
    @GetMapping("/{userId}/transactions")
    public ResponseEntity<?> getTransactionsByUserId(@PathVariable String userId,
                                                     @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword) {
        if (!isAdminPasswordValid(headerPassword)) return ResponseEntity.status(403).body(Map.of("message","Invalid admin password"));

        List<Ticket> tickets = ticketRepository.findByUserId(userId);
        if (tickets == null || tickets.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message","No tickets found for userId","userId", userId));
        }

        // Group tickets by transactionId (null transactionId kept as string "<none>")
        Map<String, List<Ticket>> grouped = tickets.stream()
                .collect(java.util.stream.Collectors.groupingBy(t -> t.getTransactionId() == null ? "<none>" : t.getTransactionId()));

        List<Map<String,Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<Ticket>> e : grouped.entrySet()){
            String txnId = e.getKey();
            List<Ticket> tks = e.getValue();
            Ticket first = tks.get(0);
            Map<String,Object> entry = new LinkedHashMap<>();
            entry.put("transactionId", txnId.equals("<none>") ? null : txnId);
            entry.put("ticketCount", tks.size());
            entry.put("ticketAmount", first.getTicketAmount() != null ? first.getTicketAmount().toPlainString() : null);
            entry.put("ticketStatus", first.getStatus() != null ? first.getStatus().name() : null);
            entry.put("ticketIds", tks.stream().map(t->t.getTicketId().toString()).toList());
            out.add(entry);
        }

        Map<String,Object> resp = new LinkedHashMap<>();
        Ticket first = tickets.get(0);
        resp.put("userId", userId);
        resp.put("fullName", first.getFullName());
        resp.put("phoneNumber", first.getPhoneNumber());
        resp.put("email", first.getEmail());
        resp.put("transactions", out);

        return ResponseEntity.ok(resp);
    }

    private boolean isAdminPasswordValid(String headerPassword) {
        return adminPassword != null && !adminPassword.isBlank() && headerPassword != null && adminPassword.equals(headerPassword);
    }

    @GetMapping("/successful")
    public ResponseEntity<?> successful(
            @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword
    ) {
        if (!isAdminPasswordValid(headerPassword)) {
            return ResponseEntity.status(403).body(Map.of("message", "Invalid admin password"));
        }

        List<String> userIds = ticketRepository.findDistinctUserIdsByStatus(TicketStatus.TRANSACTION_MADE);
        List<Map<String, Object>> out = new ArrayList<>();

        for (String userId : userIds) {
            if (userId == null || userId.isBlank()) {
                continue;
            }
            List<Ticket> tickets = ticketRepository.findByUserIdAndStatus(userId, TicketStatus.TRANSACTION_MADE);
            if (tickets == null || tickets.isEmpty()) {
                continue;
            }
            Ticket first = tickets.get(0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", userId);
            m.put("fullName", first.getFullName());
            m.put("phoneNumber", first.getPhoneNumber());
            m.put("ticketCount", tickets.size());
            m.put("ticketAmount", first.getTicketAmount());
            m.put("transactionId", first.getTransactionId());
            m.put("ticketStatus", first.getStatus() != null ? first.getStatus().name() : null);
            m.put("ticketNumbers", tickets.stream().map(t -> t.getTicketId() != null ? t.getTicketId().toString() : null).toList());
            out.add(m);
        }

        return ResponseEntity.ok(out);
    }

    public record RecordTransactionRequest(
            String showId,
            String showName,
            @NotBlank String fullName,
            String email,
            @NotBlank String phoneNumber,
            @Min(1) Integer ticketCount,
            @NotBlank String transactionId,
            @jakarta.validation.constraints.NotNull java.math.BigDecimal ticketAmount
    ) {
        public int effectiveTicketCount() {
            return ticketCount == null ? 1 : ticketCount;
        }
    }
}
