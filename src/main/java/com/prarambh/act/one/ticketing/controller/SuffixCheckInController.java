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
 * Check-in by shortened ticketId suffix.
 */
@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
@Slf4j
public class SuffixCheckInController {

    private final TicketRepository ticketRepository;
    private final TicketCheckInService ticketCheckInService;

    /**
     * Check-in a single ticket by the last 5 characters of its UUID ticketId.
     *
     * <p>If the suffix matches multiple tickets, this returns a 409 with candidates.
     */
    @PostMapping("/ticket-suffix/{suffix}")
    public ResponseEntity<?> checkInByTicketIdSuffix(@PathVariable String suffix) {
        if (suffix == null || suffix.trim().length() != 5) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "suffix must be exactly 5 characters"
            ));
        }

        String normalized = suffix.trim().toLowerCase();
        List<Ticket> matches = ticketRepository.findByTicketIdSuffix(normalized);

        if (matches.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "result", "NOT_FOUND",
                    "message", "No ticket matches suffix",
                    "suffix", normalized
            ));
        }

        if (matches.size() > 1) {
            // Ambiguous
            return ResponseEntity.status(409).body(Map.of(
                    "result", "AMBIGUOUS",
                    "message", "Multiple tickets match this suffix",
                    "suffix", normalized,
                    "candidates", matches.stream().map(t -> Map.of(
                            "ticketId", t.getTicketId(),
                            "qrCodeId", t.getQrCodeId(),
                            "status", t.getStatus().name(),
                            "phoneNumber", t.getPhoneNumber()
                    )).toList()
            ));
        }

        Ticket t = matches.get(0);
        ticketCheckInService.checkInByBarcode(t.getQrCodeId());
        log.warn("event=checkin_by_ticketid_suffix suffix={} ticketId={} qrCodeId={}", normalized, t.getTicketId(), t.getQrCodeId());

        return ResponseEntity.ok(Map.of(
                "result", "OK",
                "suffix", normalized,
                "ticketId", t.getTicketId(),
                "qrCodeId", t.getQrCodeId()
        ));
    }
}