package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.repository.AuditoriumRepository;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.TicketPurchaseCheckedInEvent;
import com.prarambh.act.one.ticketing.service.TicketPurchaseIssuedEvent;
import com.prarambh.act.one.ticketing.service.ShowSettingsService;
import com.prarambh.act.one.ticketing.service.TicketCheckInService;
import com.prarambh.act.one.ticketing.service.TicketIssuanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.prarambh.act.one.ticketing.controller.TicketController.TicketResponse.formatIstTime;

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
    private final AuditoriumRepository auditoriumRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${actone.admin.purge-password:}")
    private String adminPassword;

    private boolean isAdmin(String pass){
        return org.springframework.util.StringUtils.hasText(adminPassword)
                && org.springframework.util.StringUtils.hasText(pass)
                && pass.equals(adminPassword);
    }

    /**
     * Fetch distinct shows from the tickets table.
     */
    @GetMapping("/shows")
    public ResponseEntity<?> getShowsFromTickets() {
        List<Object[]> results = ticketRepository.findDistinctShows();
        List<Map<String, Object>> shows = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("showName", (String) row[0]);
            map.put("showId", (String) row[1]);
            shows.add(map);
        }
        return ResponseEntity.ok(shows);
    }

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

        // auditoriumId is optional; tests send no auditoriumId
        // If provided, we could verify existence, but keep optional to match tests

        // Determine showId: prefer request.showId when it looks like a generated id; otherwise generate from show name
        String resolvedShowId = request.showId();
        if (resolvedShowId == null || resolvedShowId.isBlank() || !resolvedShowId.startsWith("SHOW-")) {
            resolvedShowId = com.prarambh.act.one.ticketing.service.ShowIdGenerator.fromShowName(resolvedShowName);
        }

        // parse ticket amount (string in tests) into BigDecimal
        java.math.BigDecimal parsedAmount;
        try {
            parsedAmount = request.ticketAmount() == null ? null : new java.math.BigDecimal(request.ticketAmount());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid ticketAmount format"));
        }
        if (parsedAmount == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "ticketAmount is required"));
        }

        Ticket ticket = new Ticket();
        ticket.setShowName(resolvedShowName);
        ticket.setShowId(resolvedShowId);
        // auditoriumId is optional in integration tests; accept null
        ticket.setAuditoriumId(request.auditoriumId());
        ticket.setFullName(request.fullName());
        ticket.setEmail(request.email());
        ticket.setPhoneNumber(normalizePhoneLast10(request.phoneNumber()));
        ticket.setTicketCount(request.ticketCount() == null ? 1 : request.ticketCount());
        ticket.setTransactionId(request.transactionId());
        ticket.setTicketAmount(parsedAmount);

        List<Ticket> savedTickets = ticketIssuanceService.issueTickets(ticket);
        Ticket primary = savedTickets.get(0);

        log.info(
                "event=ticket_recorded ticketId={} qrCodeId={} showId={} showName={} status={} ticketCount={} userId={}",
                primary.getTicketId(),
                primary.getQrCodeId(),
                primary.getShowId(),
                primary.getShowName(),
                primary.getStatus(),
                primary.getTicketCount(),
                primary.getUserId());

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("ticketId", primary.getTicketId());
        response.put("status", primary.getStatus().name());
        response.put("qrCodeId", primary.getQrCodeId());
        response.put("showId", primary.getShowId());
        response.put("showName", primary.getShowName());
        response.put("auditoriumId", primary.getAuditoriumId());
        response.put("ticketCount", primary.getTicketCount());
        response.put("userId", primary.getUserId());
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
    @PostMapping("/qrcode/{qrCodeId}/checkin")
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
                    qrCodeId, ticket.getTicketId(), ticket.getUsedAtDate(), formatIstTime(ticket.getUsedAtTime()));
            return ResponseEntity.ok(Map.of(
                    "result", "ALREADY_USED",
                    "message", "Ticket has already been checked in",
                    "usedAtDate", ticket.getUsedAtDate(),
                    "usedAtTimeIst", formatIstTime(ticket.getUsedAtTime())
            ));
        }

        if (ticket.getStatus() == TicketStatus.TRANSACTION_MADE) {
            log.warn("Check-in PENDING_APPROVAL: qrCodeId={}, ticketId={}, userId={}",
                    qrCodeId, ticket.getTicketId(), ticket.getUserId());
            return ResponseEntity.ok(Map.of(
                    "result", "PENDING_APPROVAL",
                    "message", "Ticket not yet approved. Please contact admin.",
                    "userId", ticket.getUserId() != null ? ticket.getUserId() : ""
            ));
        }

        // NEW: check-in through service (publishes check-in email event)
        Ticket saved = ticketCheckInService.checkInByBarcode(qrCodeId).orElseThrow();

        log.info("Check-in VALID: qrCodeId={}, ticketId={}, set status=USED usedAtDate={} usedAtTimeIst={}",
                qrCodeId, saved.getTicketId(), saved.getUsedAtDate(), formatIstTime(saved.getUsedAtTime()));

        return ResponseEntity.ok(Map.of(
                "result", "VALID",
                "message", "Check-in successful",
                "usedAtDate", saved.getUsedAtDate(),
                "usedAtTimeIst", formatIstTime(saved.getUsedAtTime())
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

    /** Fetch all tickets sorted by creation date/time (newest first). */
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

    /** Fetch a single ticket by its UUID. */
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

    /** Fetch all tickets for a given userId. */
    @GetMapping("/by-user")
    public ResponseEntity<List<TicketResponse>> getTicketsByUserId(@RequestParam("userId") String userId) {
        log.info("Fetch tickets by userId request received: userId={}", userId);
        List<TicketResponse> tickets = ticketRepository.findByUserId(userId)
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
            @NotBlank String ticketAmount,
            String auditoriumId,
            String showId
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
            String userId,
            String transactionId,
            String status,
            java.math.BigDecimal ticketAmount,
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
                    t.getUserId(),
                    t.getTransactionId(),
                    t.getStatus() != null ? t.getStatus().name() : null,
                    t.getTicketAmount(),
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

    /**
     * Replace ALL tickets for a given {@code userId}.
     *
     * <p>Admin-only. This is a bulk operation.
     *
     * @param userId user id whose tickets should be replaced
     * @param request replacement payload applied to each ticket row
     * @param pass admin password header
     * @return list of updated tickets
     */
    @PutMapping("/by-user/{userId}")
    public ResponseEntity<?> replaceTicketsByUserId(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request,
            @RequestHeader(name = "X-Admin-Password", required = false) String pass
    ) {
        if (!isAdmin(pass)) {
            return ResponseEntity.status(403).body(Map.of("message", "admin password required"));
        }

        List<Ticket> tickets = ticketRepository.findByUserId(userId);
        if (tickets.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No tickets found for userId", "userId", userId));
        }

        // Full replace semantics for common safe fields.
        // We intentionally do NOT mutate primary identifiers like ticketId / qrCodeId.
        // Track status transitions so we can trigger emails after persistence.
        Map<UUID, TicketStatus> oldStatuses = new HashMap<>();
        for (Ticket t : tickets) {
            oldStatuses.put(t.getTicketId(), t.getStatus());
            if (request.containsKey("showName")) t.setShowName(String.valueOf(request.get("showName")));
            if (request.containsKey("showId")) t.setShowId(String.valueOf(request.get("showId")));
            if (request.containsKey("auditoriumId")) t.setAuditoriumId(String.valueOf(request.get("auditoriumId")));
            if (request.containsKey("fullName")) t.setFullName(String.valueOf(request.get("fullName")));
            if (request.containsKey("email")) t.setEmail(String.valueOf(request.get("email")));
            if (request.containsKey("phoneNumber")) t.setPhoneNumber(normalizePhoneLast10(String.valueOf(request.get("phoneNumber"))));
            if (request.containsKey("ticketCount")) t.setTicketCount(Integer.parseInt(String.valueOf(request.get("ticketCount"))));
            if (request.containsKey("transactionId")) t.setTransactionId(String.valueOf(request.get("transactionId")));
            if (request.containsKey("ticketAmount")) {
                t.setTicketAmount(new java.math.BigDecimal(String.valueOf(request.get("ticketAmount"))));
            }
            if (request.containsKey("status")) {
                t.setStatus(TicketStatus.valueOf(String.valueOf(request.get("status"))));
            }
        }

        ticketRepository.saveAll(tickets);
        triggerStatusEmailsIfNeeded(tickets, oldStatuses);
        return ResponseEntity.ok(tickets.stream().map(TicketResponse::from).toList());
    }

    /**
     * Patch ALL tickets for a given {@code userId}.
     *
     * <p>Admin-only. This is a bulk operation.
     */
    @PatchMapping("/by-user/{userId}")
    public ResponseEntity<?> patchTicketsByUserId(
            @PathVariable String userId,
            @RequestBody Map<String, Object> patch,
            @RequestHeader(name = "X-Admin-Password", required = false) String pass
    ) {
        if (!isAdmin(pass)) {
            return ResponseEntity.status(403).body(Map.of("message", "admin password required"));
        }

        List<Ticket> tickets = ticketRepository.findByUserId(userId);
        if (tickets.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No tickets found for userId", "userId", userId));
        }
        if (patch == null || patch.isEmpty()) {
            return ResponseEntity.ok(tickets.stream().map(TicketResponse::from).toList());
        }

        Map<UUID, TicketStatus> oldStatuses = new HashMap<>();
        for (Ticket t : tickets) {
            oldStatuses.put(t.getTicketId(), t.getStatus());
            if (patch.containsKey("showName")) t.setShowName(String.valueOf(patch.get("showName")));
            if (patch.containsKey("showId")) t.setShowId(String.valueOf(patch.get("showId")));
            if (patch.containsKey("auditoriumId")) t.setAuditoriumId(String.valueOf(patch.get("auditoriumId")));
            if (patch.containsKey("fullName")) t.setFullName(String.valueOf(patch.get("fullName")));
            if (patch.containsKey("email")) t.setEmail(String.valueOf(patch.get("email")));
            if (patch.containsKey("phoneNumber")) t.setPhoneNumber(normalizePhoneLast10(String.valueOf(patch.get("phoneNumber"))));
            if (patch.containsKey("ticketCount")) t.setTicketCount(Integer.parseInt(String.valueOf(patch.get("ticketCount"))));
            if (patch.containsKey("transactionId")) t.setTransactionId(String.valueOf(patch.get("transactionId")));
            if (patch.containsKey("ticketAmount")) {
                t.setTicketAmount(new java.math.BigDecimal(String.valueOf(patch.get("ticketAmount"))));
            }
            if (patch.containsKey("status")) {
                t.setStatus(TicketStatus.valueOf(String.valueOf(patch.get("status"))));
            }
        }

        ticketRepository.saveAll(tickets);
        triggerStatusEmailsIfNeeded(tickets, oldStatuses);
        return ResponseEntity.ok(tickets.stream().map(TicketResponse::from).toList());
    }

    /**
     * Delete ALL tickets for a given {@code userId}.
     */
    @DeleteMapping("/by-user/{userId}")
    public ResponseEntity<?> deleteTicketsByUserId(
            @PathVariable String userId,
            @RequestHeader(name = "X-Admin-Password", required = false) String pass
    ) {
        if (!isAdmin(pass)) {
            return ResponseEntity.status(403).body(Map.of("message", "admin password required"));
        }

        List<Ticket> tickets = ticketRepository.findByUserId(userId);
        if (tickets.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No tickets found for userId", "userId", userId));
        }

        int count = tickets.size();
        ticketRepository.deleteAllInBatch(tickets);
        return ResponseEntity.ok(Map.of("message", "deleted", "userId", userId, "deletedCount", count));
    }

    /**
     * Delete a single ticket by its ticketId (primary ticket identifier).
     * Public/admin: available to anyone for now; requires admin if you want to restrict later.
     *
     * @param ticketId the UUID ticketId to delete
     */
    @DeleteMapping("/{ticketId}")
    public ResponseEntity<?> deleteTicketByTicketId(@PathVariable UUID ticketId,
                                                     @RequestHeader(name = "X-Admin-Password", required = false) String pass) {
        // Optional: restrict to admin by uncommenting the following lines
        // if (!isAdmin(pass)) { return ResponseEntity.status(403).body(Map.of("message", "admin password required")); }

        if (ticketId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "ticketId required"));
        }

        java.util.Optional<Ticket> existing = ticketRepository.findById(ticketId);
        if (existing.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "Ticket not found", "ticketId", ticketId));
        }

        ticketRepository.delete(existing.get());
        return ResponseEntity.ok(Map.of("message", "deleted", "ticketId", ticketId));
    }

    /**
     * Update ticket status using a partial (case-insensitive) full name match and a ticketId suffix.
     * Both values are required. The ticketId suffix must be at least 5 characters long.
     * Admin-only: requires X-Admin-Password header.
     *
     * Request JSON example: { "partialName": "Aisha", "ticketIdSuffix": "3e4b4a4093", "status": "ISSUED" }
     *
     * This performs a bulk update for all tickets that match both criteria.
     */
    @PatchMapping("/status/by-name-suffix")
    public ResponseEntity<?> updateStatusByNameAndSuffix(
            @RequestBody Map<String, Object> body,
            @RequestHeader(name = "X-Admin-Password", required = false) String pass
    ) {
        if (!isAdmin(pass)) {
            return ResponseEntity.status(403).body(Map.of("message", "admin password required"));
        }

        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "request body required"));
        }

        String partialName = body.get("partialName") == null ? null : String.valueOf(body.get("partialName")).trim();
        String ticketIdSuffix = body.get("ticketIdSuffix") == null ? null : String.valueOf(body.get("ticketIdSuffix")).trim();
        String statusRaw = body.get("status") == null ? null : String.valueOf(body.get("status")).trim();

        if (partialName == null || partialName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "partialName is required"));
        }
        if (ticketIdSuffix == null || ticketIdSuffix.length() < 5) {
            return ResponseEntity.badRequest().body(Map.of("message", "ticketIdSuffix is required and must be at least 5 characters"));
        }
        if (statusRaw == null || statusRaw.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "status is required"));
        }

        final TicketStatus newStatus;
        try {
            newStatus = TicketStatus.valueOf(statusRaw);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "invalid status value", "allowed", List.of(TicketStatus.values())));
        }

        String suffixLower = ticketIdSuffix.toLowerCase();

        // First find by name (database query), then filter by ticketId suffix (UUID string)
        List<Ticket> candidates = ticketRepository.findByFullNameContainingIgnoreCase(partialName);
        List<Ticket> matched = new ArrayList<>();
        for (Ticket t : candidates) {
            if (t.getTicketId() == null) continue;
            String idStr = t.getTicketId().toString().toLowerCase();
            if (idStr.endsWith(suffixLower)) {
                matched.add(t);
            }
        }

        if (matched.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No tickets matched the provided partial name and ticketId suffix"));
        }

        Map<UUID, TicketStatus> oldStatuses = new HashMap<>();
        for (Ticket t : matched) {
            oldStatuses.put(t.getTicketId(), t.getStatus());
        }

        // Apply update
        for (Ticket t : matched) {
            t.setStatus(newStatus);
        }
        ticketRepository.saveAll(matched);

        triggerStatusEmailsIfNeeded(matched, oldStatuses);

        return ResponseEntity.ok(matched.stream().map(TicketResponse::from).toList());
    }


    /**
     * Triggers purchase-level email events when tickets transition to ISSUED or USED.
     *
     * <p>This keeps behavior consistent across controller endpoints (PATCH/PUT/bulk admin flows)
     * by publishing the same events used in the normal issuance/check-in services.
     */
    private void triggerStatusEmailsIfNeeded(List<Ticket> updatedTickets, Map<UUID, TicketStatus> oldStatuses) {
        if (updatedTickets == null || updatedTickets.isEmpty() || oldStatuses == null || oldStatuses.isEmpty()) {
            return;
        }

        // Collect tickets that actually transitioned.
        List<Ticket> transitionedToIssued = new ArrayList<>();
        List<Ticket> transitionedToUsed = new ArrayList<>();
        for (Ticket t : updatedTickets) {
            if (t == null || t.getTicketId() == null) continue;
            TicketStatus before = oldStatuses.get(t.getTicketId());
            TicketStatus after = t.getStatus();
            if (before == after) continue;

            if (after == TicketStatus.ISSUED) {
                transitionedToIssued.add(t);
            } else if (after == TicketStatus.USED) {
                transitionedToUsed.add(t);
            }
        }

        // ISSUED: publish one event per purchase group.
        if (!transitionedToIssued.isEmpty()) {
            for (List<Ticket> group : groupTicketsForEmail(transitionedToIssued)) {
                if (!group.isEmpty()) {
                    eventPublisher.publishEvent(new TicketPurchaseIssuedEvent(List.copyOf(group)));
                }
            }
        }

        // USED: publish only if the full group is now USED (no ISSUED remaining), matching TicketCheckInService.
        if (!transitionedToUsed.isEmpty()) {
            for (List<Ticket> group : groupTicketsForEmail(transitionedToUsed)) {
                if (group.isEmpty()) continue;

                Ticket sample = group.get(0);
                boolean shouldPublish = false;

                if (sample.getEmail() != null && sample.getPhoneNumber() != null && sample.getShowId() != null) {
                    long remainingIssued = ticketRepository.countByEmailIgnoreCaseAndPhoneNumberIgnoreCaseAndShowIdAndStatus(
                            sample.getEmail(),
                            sample.getPhoneNumber(),
                            sample.getShowId(),
                            TicketStatus.ISSUED
                    );
                    shouldPublish = remainingIssued == 0;
                } else if (sample.getUserId() != null) {
                    List<Ticket> userTickets = ticketRepository.findByUserId(sample.getUserId());
                    shouldPublish = userTickets.stream().noneMatch(t -> t.getStatus() == TicketStatus.ISSUED);
                    // Use userTickets as the group for the email.
                    group = userTickets;
                }

                if (shouldPublish) {
                    eventPublisher.publishEvent(new TicketPurchaseCheckedInEvent(List.copyOf(group)));
                }
            }
        }
    }

    /**
     * Groups tickets into purchase groups for email events.
     *
     * <p>Primary grouping: (email, phoneNumber, showId). Fallback: userId.
     */
    private List<List<Ticket>> groupTicketsForEmail(List<Ticket> tickets) {
        Map<String, List<Ticket>> groups = new LinkedHashMap<>();
        for (Ticket t : tickets) {
            if (t == null) continue;

            String key;
            if (t.getEmail() != null && t.getPhoneNumber() != null && t.getShowId() != null) {
                key = "E:" + t.getEmail().toLowerCase() + "|P:" + t.getPhoneNumber().toLowerCase() + "|S:" + t.getShowId();
                // Fetch the full group from DB to ensure we include all tickets.
                List<Ticket> fullGroup = ticketRepository.findByEmailIgnoreCaseAndPhoneNumberIgnoreCaseAndShowId(
                        t.getEmail(), t.getPhoneNumber(), t.getShowId()
                );
                groups.putIfAbsent(key, fullGroup);
            } else if (t.getUserId() != null) {
                key = "U:" + t.getUserId();
                List<Ticket> fullGroup = ticketRepository.findByUserId(t.getUserId());
                groups.putIfAbsent(key, fullGroup);
            }
        }

        return new ArrayList<>(groups.values());
    }
}
