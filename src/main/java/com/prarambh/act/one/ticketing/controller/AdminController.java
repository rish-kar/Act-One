package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Auditorium;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.AuditoriumService;
import com.prarambh.act.one.ticketing.service.ShowIdGenerator;
import com.prarambh.act.one.ticketing.service.ShowSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only endpoints.
 *
 * <p>These endpoints are protected by a shared secret (the "admin password"). Provide it either:
 * <ul>
 *   <li>via the {@code X-Admin-Password} header, or</li>
 *   <li>via the JSON body field {@code password}</li>
 * </ul>
 *
 * <p><strong>Security note:</strong> This is not a replacement for proper authentication/authorization.
 * It's intended for internal/dev use.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final TicketRepository ticketRepository;
    private final ShowSettingsService showSettingsService;
    private final AuditoriumService auditoriumService;

    /** Shared secret used for admin endpoints. Configure via actone.admin.purge-password. */
    @Value("${actone.admin.purge-password:}")
    private String purgePassword;

    /**
     * Validates the provided admin password.
     */
    private boolean isAdminPasswordValid(String headerPassword) {
        if (purgePassword == null || purgePassword.isBlank()) {
            return false;
        }
        return headerPassword != null && !headerPassword.isBlank() && purgePassword.equals(headerPassword);
    }

    /**
     * Deletes ALL tickets from the database.
     *
     * <p>Auth: {@code X-Admin-Password} header.
     */
    @DeleteMapping("/tickets")
    public ResponseEntity<?> deleteAllTickets(
            @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword
    ) {
        log.warn("Admin purge request received (tickets delete-all)");

        if (purgePassword == null || purgePassword.isBlank()) {
            log.warn("Purge endpoint called but no purge password is configured; refusing.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of("message", "Purge is not enabled"));
        }

        if (!isAdminPasswordValid(headerPassword)) {
            log.warn("Admin purge rejected: invalid password");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of("message", "Invalid admin password"));
        }

        long before = ticketRepository.count();
        ticketRepository.deleteAllInBatch();
        log.warn("Admin purge executed successfully: deletedCount={} ", before);

        // Recalculate all auditoriums immediately after purging all tickets.
        for (Auditorium auditorium : auditoriumService.findAll()) {
            auditoriumService.recalc(auditorium.getAuditoriumId());
        }

        return ResponseEntity.ok(java.util.Map.of(
                "message", "All tickets deleted",
                "deletedCount", before
        ));
    }

    /**
     * Get the currently configured default show settings.
     *
     * @param headerPassword admin password header
     * @return current showName (nullable) and derived showId
     */
    @GetMapping("/show-name")
    public ResponseEntity<?> getShowName(
            @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword
    ) {
        if (!isAdminPasswordValid(headerPassword)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("message", "Invalid admin password"));
        }
        String showName = showSettingsService.getDefaultShowName().orElse(null);
        String showId = ShowIdGenerator.fromShowName(showName);

        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("defaultShowName", showName);
        body.put("showId", showId);
        body.put("defaultShowDate", showSettingsService.getDefaultShowDate().orElse(null));
        body.put("defaultShowTime", showSettingsService.getDefaultShowTime().orElse(null));
        return ResponseEntity.ok(body);
    }

    /**
     * Set/clear the default show settings.
     *
     * @param req payload
     * @param headerPassword admin password header
     * @return new showName + showId
     */
    @PostMapping("/show-name")
    public ResponseEntity<?> setShowName(
            @RequestBody java.util.Map<String, Object> req,
            @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword
    ) {
        if (!isAdminPasswordValid(headerPassword)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(java.util.Map.of("message", "Invalid admin password"));
        }

        if (req != null && Boolean.TRUE.equals(req.get("clear"))) {
            showSettingsService.clearDefaultShowName();
            showSettingsService.setDefaultShowDate(null);
            showSettingsService.setDefaultShowTime(null);

            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("defaultShowName", null);
            body.put("showId", null);
            body.put("defaultShowDate", null);
            body.put("defaultShowTime", null);
            return ResponseEntity.ok(body);
        }

        String showName = req == null ? null : (req.get("showName") == null ? null : String.valueOf(req.get("showName")));
        if (showName == null || showName.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "showName required"));
        }

        java.time.LocalDate showDate = null;
        if (req != null && req.get("showDate") != null && !String.valueOf(req.get("showDate")).isBlank()) {
            showDate = java.time.LocalDate.parse(String.valueOf(req.get("showDate")));
        }

        java.time.LocalTime showTime = null;
        if (req != null && req.get("showTime") != null && !String.valueOf(req.get("showTime")).isBlank()) {
            String raw = String.valueOf(req.get("showTime")).trim();
            // Accept HH:mm (tests) and HH:mm:ss (API clients)
            if (raw.matches("^\\d{2}:\\d{2}$")) {
                raw = raw + ":00";
            }
            showTime = java.time.LocalTime.parse(raw);
        }

        showSettingsService.setDefaultShowName(showName);
        showSettingsService.setDefaultShowDate(showDate);
        showSettingsService.setDefaultShowTime(showTime);

        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("defaultShowName", showSettingsService.getDefaultShowName().orElse(null));
        body.put("showId", ShowIdGenerator.fromShowName(showName));
        body.put("defaultShowDate", showSettingsService.getDefaultShowDate().orElse(null));
        body.put("defaultShowTime", showSettingsService.getDefaultShowTime().orElse(null));
        return ResponseEntity.ok(body);
    }

    /**
     * Request body for configuring/clearing the default show name, date, and time.
     */
    public record ShowNameRequest(String showName, java.time.LocalDate showDate, java.time.LocalTime showTime, Boolean clear) {
    }
}
