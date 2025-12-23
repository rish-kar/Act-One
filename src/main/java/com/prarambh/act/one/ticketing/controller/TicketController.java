package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/tickets")
@Slf4j
@RequiredArgsConstructor
public class TicketController {

    private final TicketRepository ticketRepository;

    @PostMapping("/issue")
    public ResponseEntity<?> issueTicket(@Valid @RequestBody IssueTicketRequest request) {
        Ticket ticket = new Ticket();
        ticket.setShowName(request.showName());
        ticket.setFullName(request.fullName());
        ticket.setEmail(request.email());
        ticket.setPhoneNumber(request.phoneNumber());
        Ticket saved = ticketRepository.save(ticket);
        log.info("Issued ticket {} for show {} ({})", saved.getTicketId(), saved.getShowId(), saved.getShowName());
        return ResponseEntity.ok(Map.of(
                "ticketId", saved.getTicketId(),
                "status", saved.getStatus().name(),
                "barcodeId", saved.getBarcodeId(),
                "showId", saved.getShowId(),
                "showName", saved.getShowName()
        ));
    }

    @PostMapping("/{ticketId}/checkin")
    public ResponseEntity<?> checkIn(@PathVariable UUID ticketId) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("result", "NOT_FOUND"));
        }
        Ticket ticket = ticketOpt.get();
        if (ticket.getStatus() == TicketStatus.USED) {
            Instant usedAt = ticket.getUsedAt();
            return ResponseEntity.ok(Map.of(
                    "result", "ALREADY_USED",
                    "usedAt", usedAt != null ? usedAt.toString() : null
            ));
        }
        ticket.markUsed();
        ticketRepository.save(ticket);
        log.info("Checked-in ticket {}", ticketId);
        return ResponseEntity.ok(Map.of("result", "VALID"));
    }

    public record IssueTicketRequest(
            @NotBlank String showName,
            @NotBlank String fullName,
            @NotBlank String email,
            @NotBlank String phoneNumber
    ) {}
}
