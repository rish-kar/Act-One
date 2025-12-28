package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.ShowSettingsService;
import com.prarambh.act.one.ticketing.service.TicketCheckInService;
import com.prarambh.act.one.ticketing.service.TicketIssuanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final TicketIssuanceService ticketIssuanceService;
    private final TicketCheckInService ticketCheckInService;

    /**
     * Issue a ticket.
     *
     * <p>If {@code showName} is omitted/blank in the request, the controller will attempt to
     * use the admin-configured default show name (see {@code /api/admin/show-name}).
     *
     * @param request issue payload
     * @return ticket id, qrCode Id, status, and show info
     */
    @PostMapping("/issue")
    public ResponseEntity<?> issueTicket(@Valid @RequestBody IssueTicketRequest request) {
        log.info("Issue ticket request received: fullName='{}', email='{}', phoneNumber='{}', showName='{}', ticketCount={}",
                request.fullName(), request.email(), request.phoneNumber(), request.showName(), request.ticketCount());

        String resolvedShowName = request.showName();
        if (resolvedShowName == null || resolvedShowName.isBlank()) {
            resolvedShowName = showSettingsService.getDefaultShowName().orElse(null);
            log.info("No showName provided; resolved from defaultShowName='{}'", resolvedShowName);
        }
        if (resolvedShowName == null || resolvedShowName.isBlank()) {
            log.warn("Issue ticket rejected: no showName provided and no default is configured");
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "showName is required (or set a default via /api/admin/show-name)"
            ));
        }

        Ticket ticket = new Ticket();
        ticket.setShowName(resolvedShowName);
        ticket.setFullName(request.fullName());
        ticket.setEmail(request.email());
        ticket.setPhoneNumber(normalizePhoneLast10(request.phoneNumber()));
        ticket.setTicketCount(request.ticketCount() == null ? 1 : request.ticketCount());
        ticket.setTransactionId(request.transactionId());
        ticket.setTicketAmount(request.ticketAmount());

        List<Ticket> savedTickets = ticketIssuanceService.issueTickets(ticket);
        Ticket primary = savedTickets.get(0);

        log.info(
                "event=ticket_recorded ticketId={} qrCodeId={} showId={} showName={} status={} ticketCount={} customerId={}",
                primary.getTicketId(),
                primary.getQrCodeId(),
                primary.getShowId(),
                primary.getShowName(),
                primary.getStatus(),
                primary.getTicketCount(),
                primary.getCustomerId());

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("ticketId", primary.getTicketId());
        response.put("status", primary.getStatus().name());
        response.put("qrCodeId", primary.getQrCodeId());
        response.put("showId", primary.getShowId());
        response.put("showName", primary.getShowName());
        response.put("ticketCount", primary.getTicketCount());
        response.put("customerId", primary.getCustomerId());
        response.put("transactionId", primary.getTransactionId());
        response.put("ticketIds", savedTickets.stream().map(Ticket::getTicketId).collect(Collectors.toList()));
        response.put("qrCodeIds", savedTickets.stream().map(Ticket::getQrCodeId).collect(Collectors.toList()));
        response.put("message", "Transaction recorded. Tickets will be issued after manual approval.");

        return ResponseEntity.ok(response);
    }

    /**
     * Check in a ticket using its qrCode Id.
     *
     * <p>This is the primary endpoint intended for scanning at the venue.
     */
    @PostMapping("/barcode/{qrCodeId}/checkin")
    public ResponseEntity<?> checkInByBarcode(@PathVariable String qrCodeId) {
        log.info("Check-in request received: qrCodeId={}", qrCodeId);

        Optional<Ticket> ticketOpt = ticketRepository.findByQrCodeId(qrCodeId);
        if (ticketOpt.isEmpty()) {
            log.warn("Check-in NOT_FOUND: qrCodeId={}", qrCodeId);
            return ResponseEntity.ok(Map.of(
                    "result", "NOT_FOUND",
                    "message", "Ticket not found"
            ));
        }

        Ticket ticket = ticketOpt.get();
        if (ticket.getStatus() == TicketStatus.USED) {
            log.info("Check-in ALREADY_USED: qrCodeId={}, ticketId={}, usedAtDate={}, usedAtTimeIst={} ",
                    qrCodeId, ticket.getTicketId(), ticket.getUsedAtDate(), TicketResponse.formatIstTime(ticket.getUsedAtTime()));
            return ResponseEntity.ok(Map.of(
                    "result", "ALREADY_USED",
                    "message", "Ticket has already been checked in",
                    "usedAtDate", ticket.getUsedAtDate(),
                    "usedAtTimeIst", TicketResponse.formatIstTime(ticket.getUsedAtTime())
            ));
        }

        if (ticket.getStatus() == TicketStatus.TRANSACTION_MADE) {
            log.warn("Check-in PENDING_APPROVAL: qrCodeId={}, ticketId={}, customerId={}",
                    qrCodeId, ticket.getTicketId(), ticket.getCustomerId());
            return ResponseEntity.ok(Map.of(
                    "result", "PENDING_APPROVAL",
                    "message", "Ticket not yet approved. Please contact admin.",
                    "customerId", ticket.getCustomerId() != null ? ticket.getCustomerId() : 0
            ));
        }

        // NEW: check-in through service (publishes check-in email event)
        Ticket saved = ticketCheckInService.checkInByBarcode(qrCodeId).orElseThrow();

        log.info("Check-in VALID: qrCodeId={}, ticketId={}, set status=USED usedAtDate={} usedAtTimeIst={}",
                qrCodeId, saved.getTicketId(), saved.getUsedAtDate(), TicketResponse.formatIstTime(saved.getUsedAtTime()));

        return ResponseEntity.ok(Map.of(
                "result", "VALID",
                "message", "Check-in successful",
                "usedAtDate", saved.getUsedAtDate(),
                "usedAtTimeIst", TicketResponse.formatIstTime(saved.getUsedAtTime())
        ));
    }

    /**
     * Fetch a single ticket by its UUID ticket id.
     *
     * <p>This endpoint is intended for admin/debug usage (UUID is not scannable).
     */
    @GetMapping("/by-ticket-id/{ticketId}")
    public ResponseEntity<?> getTicketByTicketId(@PathVariable UUID ticketId) {
        log.info("Fetch ticket by ticketId request received: ticketId={}", ticketId);

        return ticketRepository.findById(ticketId)
                .<ResponseEntity<?>>map(ticket -> {
                    log.info("Fetch ticket by ticketId found: ticketId={}, qrCodeId={}, status={}, showId={}, showName='{}'",
                            ticket.getTicketId(), ticket.getQrCodeId(), ticket.getStatus(), ticket.getShowId(), ticket.getShowName());
                    return ResponseEntity.ok(TicketResponse.from(ticket));
                })
                .orElseGet(() -> {
                    log.warn("Fetch ticket by ticketId NOT_FOUND: ticketId={}", ticketId);
                    return ResponseEntity.status(404).body(Map.of(
                            "message", "Ticket not found",
                            "ticketId", ticketId
                    ));
                });
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
        log.warn("Deprecated check-in endpoint used (ticketId). Prefer the QR-based endpoint /api/tickets/barcode/{qrCodeId}/checkin. ticketId={}", ticketId);
        log.info("Check-in request received: ticketId={}", ticketId);

        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            log.warn("Check-in NOT_FOUND: ticketId={}", ticketId);
            return ResponseEntity.ok(Map.of(
                    "result", "NOT_FOUND",
                    "message", "Ticket not found"
            ));
        }

        Ticket ticket = ticketOpt.get();
        if (ticket.getStatus() == TicketStatus.USED) {
            log.info("Check-in ALREADY_USED: ticketId={}, usedAtDate={}, usedAtTimeIst={} ",
                    ticketId, ticket.getUsedAtDate(), TicketResponse.formatIstTime(ticket.getUsedAtTime()));
            return ResponseEntity.ok(Map.of(
                    "result", "ALREADY_USED",
                    "message", "Ticket has already been checked in",
                    "usedAtDate", ticket.getUsedAtDate(),
                    "usedAtTimeIst", TicketResponse.formatIstTime(ticket.getUsedAtTime())
            ));
        }

        if (ticket.getStatus() == TicketStatus.TRANSACTION_MADE) {
            log.warn("Check-in PENDING_APPROVAL: ticketId={}, customerId={}", ticketId, ticket.getCustomerId());
            return ResponseEntity.ok(Map.of(
                    "result", "PENDING_APPROVAL",
                    "message", "Ticket not yet approved. Please contact admin.",
                    "customerId", ticket.getCustomerId() != null ? ticket.getCustomerId() : 0
            ));
        }

        ticket.markUsed();
        ticketRepository.save(ticket);
        log.info("Check-in VALID: ticketId={}, set status=USED usedAtDate={} usedAtTimeIst={}",
                ticketId, ticket.getUsedAtDate(), TicketResponse.formatIstTime(ticket.getUsedAtTime()));

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
        log.info("Fetch all tickets request received");

        List<TicketResponse> tickets = ticketRepository
                .findAll(Sort.by(
                        Sort.Order.desc("createdAtDate"),
                        Sort.Order.desc("createdAtTime")
                ))
                .stream()
                .map(TicketResponse::from)
                .toList();

        log.info("Fetch all tickets completed: count={}", tickets.size());
        return ResponseEntity.ok(tickets);
    }

    /**
     * Fetch a single ticket by its UUID.
     */
    @GetMapping("/{ticketId}")
    public ResponseEntity<?> getTicketById(@PathVariable UUID ticketId) {
        log.info("Fetch ticket by id request received: ticketId={}", ticketId);

        return ticketRepository.findById(ticketId)
                .<ResponseEntity<?>>map(ticket -> {
                    log.info("Fetch ticket by id found: ticketId={}, status={}, showId={}, showName='{}'",
                            ticket.getTicketId(), ticket.getStatus(), ticket.getShowId(), ticket.getShowName());
                    return ResponseEntity.ok(TicketResponse.from(ticket));
                })
                .orElseGet(() -> {
                    log.warn("Fetch ticket by id NOT_FOUND: ticketId={}", ticketId);
                    return ResponseEntity.status(404).body(Map.of(
                            "message", "Ticket not found",
                            "ticketId", ticketId
                    ));
                });
    }

    /** Fetch all tickets for a given customerId. */
    @GetMapping("/by-customer")
    public ResponseEntity<List<TicketResponse>> getTicketsByCustomerId(@RequestParam("customerId") Integer customerId) {
        log.info("Fetch tickets by customerId request received: customerId={}", customerId);
        List<TicketResponse> tickets = ticketRepository.findByCustomerId(customerId)
                .stream()
                .map(TicketResponse::from)
                .toList();
        return ResponseEntity.ok(tickets);
    }

    /** Fetch tickets by full name (case-insensitive contains match). */
    @GetMapping("/by-name")
    public ResponseEntity<List<TicketResponse>> getTicketsByFullName(@RequestParam("fullName") String fullName) {
        log.info("Fetch tickets by fullName request received: fullName='{}'", fullName);
        String q = fullName == null ? "" : fullName.trim();
        List<TicketResponse> tickets = ticketRepository.findByFullNameContainingIgnoreCase(q)
                .stream()
                .map(TicketResponse::from)
                .toList();
        return ResponseEntity.ok(tickets);
    }

    /** Fetch tickets by phone number (matches by last 10 digits). */
    @GetMapping("/by-phone")
    public ResponseEntity<List<TicketResponse>> getTicketsByPhone(@RequestParam("phoneNumber") String phoneNumber) {
        String last10 = normalizePhoneLast10(phoneNumber);
        log.info("Fetch tickets by phone request received: phoneNumber='{}' last10='{}'", phoneNumber, last10);
        List<TicketResponse> tickets = ticketRepository.findByPhoneNumberEndingWith(last10)
                .stream()
                .map(TicketResponse::from)
                .toList();
        return ResponseEntity.ok(tickets);
    }

    /** Fetch tickets by email (case-insensitive exact match). */
    @GetMapping("/by-email")
    public ResponseEntity<List<TicketResponse>> getTicketsByEmail(@RequestParam("email") String email) {
        String q = email == null ? "" : email.trim();
        log.info("Fetch tickets by email request received: email='{}'", q);
        List<TicketResponse> tickets = ticketRepository.findByEmailIgnoreCase(q)
                .stream()
                .map(TicketResponse::from)
                .toList();
        return ResponseEntity.ok(tickets);
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
            @NotBlank String phoneNumber,
            @Min(1) Integer ticketCount,
            @NotBlank String transactionId,
            @jakarta.validation.constraints.NotNull java.math.BigDecimal ticketAmount
    ) {}

    /**
     * DTO returned by ticket listing and get-by-id endpoints.
     */
    public record TicketResponse(
            UUID ticketId,
            String qrCodeId,
            String showId,
            String showName,
            String fullName,
            String email,
            String phoneNumber,
            Integer customerId,
            String transactionId,
            String status,
            Integer ticketCount,
            String id,
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
                    t.getQrCodeId(),
                    t.getShowId(),
                    t.getShowName(),
                    t.getFullName(),
                    t.getEmail(),
                    t.getPhoneNumber(),
                    t.getCustomerId(),
                    t.getTransactionId(),
                    t.getStatus() != null ? t.getStatus().name() : null,
                    t.getTicketCount(),
                    t.getTransactionId(),
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
}
