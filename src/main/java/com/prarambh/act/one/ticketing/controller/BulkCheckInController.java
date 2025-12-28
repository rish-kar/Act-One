package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.TicketCheckInService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Bulk check-in endpoints.
 */
@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
@Slf4j
public class BulkCheckInController {

    private final TicketRepository ticketRepository;
    private final TicketCheckInService ticketCheckInService;

    /**
     * Check-in all tickets associated with a phone number (last 10 digits).
     *
     * <p>The caller must pass exactly 10 digits (no country code prefix).
     */
    @PostMapping("/phone/{phoneLast10}")
    public ResponseEntity<?> checkInByPhone(@PathVariable String phoneLast10) {
        String last10 = normalizeLast10Digits(phoneLast10);
        if (last10 == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "phone must be exactly 10 digits"
            ));
        }

        List<Ticket> tickets = ticketRepository.findByPhoneNumberEndingWith(last10);
        if (tickets.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "result", "NOT_FOUND",
                    "message", "No tickets found for phone",
                    "phoneLast10", last10,
                    "checkedIn", 0
            ));
        }

        int checkedIn = 0;
        for (Ticket t : tickets) {
            // Use existing check-in flow (side effects consistent).
            ticketCheckInService.checkInByBarcode(t.getQrCodeId());
            checkedIn++;
        }

        log.warn("event=bulk_checkin_by_phone phoneLast10={} totalTickets={} checkedIn={}", last10, tickets.size(), checkedIn);

        return ResponseEntity.ok(Map.of(
                "result", "OK",
                "phoneLast10", last10,
                "totalTickets", tickets.size(),
                "checkedIn", checkedIn
        ));
    }

    static String normalizeLast10Digits(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.trim();
        if (!digits.matches("\\d{10}")) {
            return null;
        }
        return digits;
    }
}
