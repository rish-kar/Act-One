package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.ShowSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Ticket-facing API.
 *
 * <p>This controller provides endpoints for:
 * <ul>
 *   <li>Issuing a ticket</li>
 *   <li>Checking-in a ticket (one-time use)</li>
 *   <li>Listing and fetching tickets (for admin/UI usage)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tickets")
@Slf4j
@RequiredArgsConstructor
public class TicketController {

    private final TicketRepository ticketRepository;
    private final ShowSettingsService showSettingsService;

    /**
     * Issue a ticket.
     *
     * <p>If {@code showName} is omitted/blank in the request, the controller will attempt to
     * use the admin-configured default show name (see {@code /api/admin/show-name}).
     *
     * @param request issue payload
     * @return ticket id, barcode id, status, and show info
     */
    @PostMapping("/issue")
    public ResponseEntity<?> issueTicket(@Valid @RequestBody IssueTicketRequest request) {
        String resolvedShowName = request.showName();
        if (resolvedShowName == null || resolvedShowName.isBlank()) {
            resolvedShowName = showSettingsService.getDefaultShowName().orElse(null);
        }
        if (resolvedShowName == null || resolvedShowName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "showName is required (or set a default via /api/admin/show-name)"
            ));
        }

        Ticket ticket = new Ticket();
        ticket.setShowName(resolvedShowName);
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

    /**
     * Check in a ticket.
     *
     * <p>Returns one of:
     * <ul>
     *   <li>{@code VALID} and marks the ticket as {@code USED}</li>
     *   <li>{@code ALREADY_USED} when it has already been checked in</li>
     *   <li>{@code NOT_FOUND} for unknown ticket id</li>
     * </ul>
     */
    @PostMapping("/{ticketId}/checkin")
    public ResponseEntity<?> checkIn(@PathVariable UUID ticketId) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "result", "NOT_FOUND",
                    "message", "Ticket not found"
            ));
        }

        Ticket ticket = ticketOpt.get();
        if (ticket.getStatus() == TicketStatus.USED) {
            return ResponseEntity.ok(Map.of(
                    "result", "ALREADY_USED",
                    "message", "Ticket has already been checked in",
                    "usedAtDate", ticket.getUsedAtDate(),
                    "usedAtTimeIst", TicketResponse.formatIstTime(ticket.getUsedAtTime())
            ));
        }

        ticket.markUsed();
        ticketRepository.save(ticket);
        log.info("Checked-in ticket {}", ticketId);

        return ResponseEntity.ok(Map.of(
                "result", "VALID",
                "message", "Check-in successful",
                "usedAtDate", ticket.getUsedAtDate(),
                "usedAtTimeIst", TicketResponse.formatIstTime(ticket.getUsedAtTime())
        ));
    }

    /**
     * Fetch all tickets sorted by creation date/time (newest first).
     */
    @GetMapping("/all")
    public ResponseEntity<List<TicketResponse>> getAllTickets() {
        List<TicketResponse> tickets = ticketRepository
                .findAll(Sort.by(
                        Sort.Order.desc("createdAtDate"),
                        Sort.Order.desc("createdAtTime")
                ))
                .stream()
                .map(TicketResponse::from)
                .toList();

        return ResponseEntity.ok(tickets);
    }

    /**
     * Fetch a single ticket by its UUID.
     */
    @GetMapping("/{ticketId}")
    public ResponseEntity<?> getTicketById(@PathVariable UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .<ResponseEntity<?>>map(ticket -> ResponseEntity.ok(TicketResponse.from(ticket)))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "message", "Ticket not found",
                        "ticketId", ticketId
                )));
    }

    /**
     * Request payload for issuing tickets.
     *
     * <p>{@code showName} is optional. If not provided, a default show name must have been set
     * via {@code /api/admin/show-name}.
     */
    public record IssueTicketRequest(
            String showName,
            @NotBlank String fullName,
            @NotBlank String email,
            @NotBlank String phoneNumber
    ) {}

    /**
     * DTO returned by ticket listing and get-by-id endpoints.
     */
    public record TicketResponse(
            UUID ticketId,
            UUID barcodeId,
            String showId,
            String showName,
            String fullName,
            String email,
            String phoneNumber,
            String status,
            LocalDate createdAtDate,
            String createdAtTimeIst,
            LocalDate usedAtDate,
            String usedAtTimeIst
    ) {
        /** 12-hour clock formatter used for consistent API responses. */
        private static final DateTimeFormatter IST_12H_TIME = DateTimeFormatter.ofPattern("hh:mm a");

        static TicketResponse from(Ticket t) {
            return new TicketResponse(
                    t.getTicketId(),
                    t.getBarcodeId(),
                    t.getShowId(),
                    t.getShowName(),
                    t.getFullName(),
                    t.getEmail(),
                    t.getPhoneNumber(),
                    t.getStatus() != null ? t.getStatus().name() : null,
                    t.getCreatedAtDate(),
                    formatIstTime(t.getCreatedAtTime()),
                    t.getUsedAtDate(),
                    formatIstTime(t.getUsedAtTime())
            );
        }

        /**
         * Formats a time in 12-hour form for API readability.
         */
        static String formatIstTime(LocalTime time) {
            return time == null ? null : time.format(IST_12H_TIME);
        }
    }
}
