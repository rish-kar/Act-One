package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.ManualTransactionService;
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
                "customerId", result.customerId(),
                "ticketCount", result.ticketCount(),
                "status", TicketStatus.TRANSACTION_MADE.name()
        ));
    }

    /**
     * Admin endpoint: validate transaction and issue tickets for a customer.
     */
    @PostMapping("/{customerId}/validate")
    public ResponseEntity<?> validateAndIssue(@PathVariable int customerId) {
        List<Ticket> issued = manualTransactionService.validateTransactionAndIssueTickets(customerId);
        if (issued.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Customer not found", "customerId", customerId));
        }
        Ticket first = issued.get(0);
        return ResponseEntity.ok(Map.of(
                "customerId", customerId,
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
        String last10 = normalizePhoneLast10(phoneNumber);
        List<Ticket> tickets = ticketRepository.findByPhoneNumberEndingWith(last10);
        return summarizeTickets(tickets);
    }

    private static String normalizePhoneLast10(String input) {
        if (input == null) {
            return null;
        }
        String digits = input.replaceAll("\\D", "");
        if (digits.length() <= 10) {
            return digits;
        }
        return digits.substring(digits.length() - 10);
    }

    /**
     * Retrieve transaction details by customer name.
     */
    @GetMapping("/by-name")
    public ResponseEntity<?> getByName(@RequestParam String fullName) {
        List<Ticket> tickets = ticketRepository.findByFullNameIgnoreCase(fullName);
        return summarizeTickets(tickets);
    }

    private ResponseEntity<?> summarizeTickets(List<Ticket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No matching records"));
        }
        Ticket first = tickets.get(0);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", first.getCustomerId());
        payload.put("fullName", first.getFullName());
        payload.put("phoneNumber", first.getPhoneNumber());
        payload.put("ticketCount", tickets.size());
        payload.put("ticketAmount", first.getTicketAmount() != null ? first.getTicketAmount().toPlainString() : null);
        payload.put("transactionId", first.getTransactionId());
        payload.put("ticketStatus", first.getStatus() != null ? first.getStatus().name() : null);
        return ResponseEntity.ok(payload);
    }

    /**
     * Admin endpoint: issue tickets by customer ID.
     */
    @PostMapping("/{customerId}/issue")
    public ResponseEntity<?> issueByCustomerId(@PathVariable int customerId) {
        List<Ticket> issued = manualTransactionService.validateTransactionAndIssueTickets(customerId);
        if (issued.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Customer not found", "customerId", customerId));
        }
        return ResponseEntity.ok(Map.of(
                "message", "Ticket is confirmed",
                "customerId", customerId,
                "issuedCount", issued.size()
        ));
    }

    /**
     * Admin endpoint: check-in by customer ID.
     */
    @PostMapping("/{customerId}/checkin")
    public ResponseEntity<?> checkInByCustomerId(@PathVariable int customerId) {
        int used = manualTransactionService.checkInByCustomerId(customerId);
        if (used == 0) {
            return ResponseEntity.status(404).body(Map.of("message", "Customer not found", "customerId", customerId));
        }
        return ResponseEntity.ok(Map.of(
                "message", "Check-in successful",
                "customerId", customerId,
                "usedCount", used
        ));
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

        List<Integer> customerIds = ticketRepository.findDistinctCustomerIdsByStatus(TicketStatus.TRANSACTION_MADE);
        List<Map<String, Object>> customers = new ArrayList<>();
        for (Integer customerId : customerIds) {
            List<Ticket> tickets = ticketRepository.findByCustomerIdAndStatus(customerId, TicketStatus.TRANSACTION_MADE);
            if (tickets.isEmpty()) {
                continue;
            }
            Ticket first = tickets.get(0);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("customerId", customerId);
            entry.put("fullName", first.getFullName());
            entry.put("phoneNumber", first.getPhoneNumber());
            entry.put("ticketCount", tickets.size());
            entry.put("ticketAmount", first.getTicketAmount() != null ? first.getTicketAmount().toPlainString() : null);
            entry.put("transactionId", first.getTransactionId());
            entry.put("ticketStatus", TicketStatus.TRANSACTION_MADE.name());
            customers.add(entry);
        }
        return ResponseEntity.ok(customers);
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
