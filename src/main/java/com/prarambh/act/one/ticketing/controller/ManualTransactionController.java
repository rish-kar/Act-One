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

    /**
     * Record a successful transaction (manual verification pending).
     *
     * <p>Creates ticket rows in TRANSACTION_MADE and assigns a 4-digit customerId.
     */
    @GetMapping("/record")
    public ResponseEntity<?> getTransactionDetails(@RequestParam int customerId) {
        Ticket latest = ticketRepository.findFirstByCustomerIdAndStatusOrderByCreatedAtDateDescCreatedAtTimeDesc(customerId, TicketStatus.TRANSACTION_MADE);
        if (latest == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "message", "Customer not found",
                    "customerId", customerId
            ));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customerId", latest.getCustomerId());
        response.put("fullName", latest.getFullName());
        response.put("phoneNumber", latest.getPhoneNumber());
        response.put("ticketCount", latest.getTicketCount());
        response.put("transactionId", latest.getTransactionId());
        response.put("ticketAmount", latest.getTicketAmount());

        return ResponseEntity.ok(response);
    }

    /**
     * List customers who have at least one ticket in TRANSACTION_MADE.
     */
    @GetMapping("/successful")
    public ResponseEntity<?> getSuccessfulTransactions() {
        List<Integer> customerIds = ticketRepository.findDistinctCustomerIdsByStatus(TicketStatus.TRANSACTION_MADE);

        List<Map<String, Object>> customers = new ArrayList<>();
        for (Integer customerId : customerIds) {
            List<Ticket> tickets = ticketRepository.findByCustomerId(customerId);
            if (tickets.isEmpty()) {
                continue;
            }
            Ticket first = tickets.get(0);

            List<String> txnIds = tickets.stream()
                    .map(Ticket::getTransactionId)
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .toList();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("customerId", customerId);
            entry.put("fullName", first.getFullName());
            entry.put("phoneNumber", first.getPhoneNumber());
            entry.put("email", first.getEmail());
            entry.put("ticketStatus", TicketStatus.TRANSACTION_MADE.name());
            entry.put("ticketCount", tickets.size());
            entry.put("transactionIds", txnIds);
            customers.add(entry);
        }

        return ResponseEntity.ok(customers);
    }

    /**
     * Admin endpoint: validate transaction and issue tickets for a customer.
     */
    @PostMapping("/{customerId}/validate")
    public ResponseEntity<?> validateAndIssue(@PathVariable int customerId) {
        List<Ticket> issued = manualTransactionService.validateTransactionAndIssueTickets(customerId);

        // Get transaction details from the first ticket
        Ticket first = issued.get(0);
        List<String> txnIds = issued.stream()
                .map(Ticket::getTransactionId)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();

        return ResponseEntity.ok(Map.of(
                "customerId", customerId,
                "fullName", first.getFullName(),
                "email", first.getEmail() != null ? first.getEmail() : "",
                "phoneNumber", first.getPhoneNumber(),
                "transactionIds", txnIds,
                "issuedCount", issued.size(),
                "newStatus", TicketStatus.ISSUED.name()
        ));
    }

    public record RecordTransactionRequest(
            String showId,
            String showName,
            @NotBlank String fullName,
            String email,
            @NotBlank String phoneNumber,
            @Min(1) Integer ticketCount,
            @NotBlank String transactionId
    ) {

        public int effectiveTicketCount() {
            return ticketCount == null ? 1 : ticketCount;
        }
    }
}
