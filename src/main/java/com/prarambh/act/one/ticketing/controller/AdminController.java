package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.ShowIdGenerator;
import com.prarambh.act.one.ticketing.service.ShowSettingsService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                    .body(Map.of("message", "Purge is not enabled"));
        }

        if (!isAdminPasswordValid(headerPassword)) {
            log.warn("Admin purge rejected: invalid password");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Invalid admin password"));
        }

        long before = ticketRepository.count();
        ticketRepository.deleteAllInBatch();
        log.warn("Admin purge executed successfully: deletedCount={}", before);

        return ResponseEntity.ok(Map.of(
                "message", "All tickets deleted",
                "deletedCount", before
        ));
    }

    /**
     * Returns the currently configured default show name used to auto-populate incoming tickets.
     */
    @GetMapping("/show-name")
    public ResponseEntity<?> getDefaultShowName(
            @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword
    ) {
        log.info("Admin get default show-name request received");

        if (purgePassword == null || purgePassword.isBlank()) {
            log.warn("Admin get show-name refused: admin endpoints not enabled");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Admin endpoints are not enabled"));
        }
        if (!isAdminPasswordValid(headerPassword)) {
            log.warn("Admin get show-name rejected: invalid password");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Invalid admin password"));
        }

        String current = showSettingsService.getDefaultShowName().orElse(null);
        log.info("Admin get show-name returned: defaultShowName='{}'", current);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("defaultShowName", current);
        return ResponseEntity.ok(resp);
    }

    /**
     * Sets (or clears) the default show name.
     */
    @PostMapping("/show-name")
    @Transactional
    public ResponseEntity<?> setDefaultShowName(
            @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword,
            @RequestBody(required = false) ShowNameRequest body
    ) {
        log.warn("Admin set default show-name request received: clear={}, showName='{}'",
                body != null ? body.clear() : null,
                body != null ? body.showName() : null);

        if (purgePassword == null || purgePassword.isBlank()) {
            log.warn("Admin set show-name refused: admin endpoints not enabled");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Admin endpoints are not enabled"));
        }

        if (!isAdminPasswordValid(headerPassword)) {
            log.warn("Admin set show-name rejected: invalid password");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Invalid admin password"));
        }

        boolean clear = body != null && Boolean.TRUE.equals(body.clear());
        if (clear) {
            showSettingsService.clearDefaultShowName();
            log.warn("Default show name cleared by admin.");

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("message", "Default show name cleared");
            resp.put("updatedTickets", 0);
            return ResponseEntity.ok(resp);
        }

        String showName = body != null ? body.showName() : null;
        if (showName == null || showName.isBlank()) {
            log.warn("Admin set show-name rejected: missing showName while clear!=true");
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "showName is required unless clear=true"
            ));
        }

        showSettingsService.setDefaultShowName(showName);
        String trimmed = showName.trim();

        String showId = ShowIdGenerator.fromShowName(trimmed);
        int updated = ticketRepository.updateShowNameAndShowIdForAll(trimmed, showId);
        log.warn("Default show name set by admin: defaultShowName='{}', showId={}, updatedTickets={}", trimmed, showId, updated);

        return ResponseEntity.ok(Map.of(
                "message", "Default show name set",
                "defaultShowName", trimmed,
                "showId", showId,
                "updatedTickets", updated
        ));
    }

    /**
     * Request body for configuring/clearing the default show name.
     */
    public record ShowNameRequest(String showName, Boolean clear) {
    }
}
