package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.repository.TicketRepository;
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
    private boolean isAdminPasswordValid(String headerPassword, String bodyPassword) {
        if (purgePassword == null || purgePassword.isBlank()) {
            return false;
        }
        String provided = (headerPassword != null && !headerPassword.isBlank())
                ? headerPassword
                : (bodyPassword != null && !bodyPassword.isBlank() ? bodyPassword : null);
        return provided != null && purgePassword.equals(provided);
    }

    /**
     * Deletes ALL tickets from the database.
     *
     * <p>Auth: {@code X-Admin-Password} header or body {@code {"password":"..."}}.
     */
    @DeleteMapping("/tickets")
    public ResponseEntity<?> deleteAllTickets(
            @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword,
            @RequestBody(required = false) PurgeRequest body
    ) {
        if (purgePassword == null || purgePassword.isBlank()) {
            log.warn("Purge endpoint called but no purge password is configured; refusing.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Purge is not enabled"));
        }

        if (!isAdminPasswordValid(headerPassword, body != null ? body.password() : null)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Invalid admin password"));
        }

        long before = ticketRepository.count();
        ticketRepository.deleteAllInBatch();
        log.warn("Admin purge executed. Deleted {} tickets.", before);

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
            @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword,
            @RequestBody(required = false) PurgeRequest body
    ) {
        if (purgePassword == null || purgePassword.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Admin endpoints are not enabled"));
        }
        if (!isAdminPasswordValid(headerPassword, body != null ? body.password() : null)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Invalid admin password"));
        }

        // NOTE: Map.of would throw if the value is null.
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("defaultShowName", showSettingsService.getDefaultShowName().orElse(null));
        return ResponseEntity.ok(resp);
    }

    /**
     * Sets (or clears) the default show name.
     *
     * <p>Extra behavior: when a new default is set, all existing tickets are updated to reflect
     * the new show name, so the change is visible everywhere.
     */
    @PostMapping("/show-name")
    @Transactional
    public ResponseEntity<?> setDefaultShowName(
            @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword,
            @RequestBody(required = false) ShowNameRequest body
    ) {
        if (purgePassword == null || purgePassword.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Admin endpoints are not enabled"));
        }

        if (!isAdminPasswordValid(headerPassword, body != null ? body.password() : null)) {
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
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "showName is required unless clear=true"
            ));
        }

        showSettingsService.setDefaultShowName(showName);
        String trimmed = showName.trim();

        int updated = ticketRepository.updateShowNameForAll(trimmed);
        log.warn("Default show name set by admin: {} (updated {} existing tickets)", trimmed, updated);

        return ResponseEntity.ok(Map.of(
                "message", "Default show name set",
                "defaultShowName", trimmed,
                "updatedTickets", updated
        ));
    }

    /**
     * Minimal body wrapper for endpoints that accept a password.
     */
    public record PurgeRequest(String password) {
    }

    /**
     * Request body for configuring/clearing the default show name.
     */
    public record ShowNameRequest(String password, String showName, Boolean clear) {
    }
}
