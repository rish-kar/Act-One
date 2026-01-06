package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Auditorium;
import com.prarambh.act.one.ticketing.service.AuditoriumService;
import com.prarambh.act.one.ticketing.service.ShowIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auditoriums")
@RequiredArgsConstructor
@Slf4j
public class AuditoriumController {

    private final AuditoriumService auditoriumService;

    @Value("${actone.admin.purge-password:}")
    private String adminPassword;

    private boolean isAdmin(String pass) {
        return StringUtils.hasText(adminPassword) && StringUtils.hasText(pass) && pass.equals(adminPassword);
    }

    @GetMapping
    public ResponseEntity<List<Auditorium>> getAll() {
        return ResponseEntity.ok(auditoriumService.findAll());
    }

    @GetMapping("/{auditoriumId}")
    public ResponseEntity<?> getById(@PathVariable String auditoriumId) {
        return auditoriumService.findById(auditoriumId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("message", "Auditorium not found")));
    }

    /** Get available seats for an auditorium. Admin header required. */
    @GetMapping("/{auditoriumId}/available-seats")
    public ResponseEntity<?> getAvailableSeats(@PathVariable String auditoriumId,
                                               @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword) {
        if (!isAdmin(headerPassword)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "admin password required"));
        }
        return auditoriumService.findById(auditoriumId)
                .<ResponseEntity<?>>map(a -> ResponseEntity.ok(Map.of(
                        "auditoriumId", a.getAuditoriumId(),
                        "availableSeats", a.getAvailableSeats()
                )))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("message", "Auditorium not found")));
    }

    /**
     * Create (or upsert) an auditorium. Admin header required.
     */
    @PostMapping
    public ResponseEntity<?> createAuditorium(@Valid @RequestBody AuditoriumCreateRequest req,
                                              @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword) {
        if (!isAdmin(headerPassword)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "admin password required"));
        }

        String showId = ShowIdGenerator.fromShowName(req.showName());
        Auditorium a = auditoriumService.upsert(showId, req.showName(), req.auditoriumName(), req.showDate(), req.showTime(), req.ticketAmount(), req.totalSeats(), req.reservedSeats());
        return ResponseEntity.ok(a);
    }

    /**
     * Replace an auditorium by id. Admin header required.
     */
    @PutMapping("/{auditoriumId}")
    public ResponseEntity<?> replaceAuditorium(@PathVariable String auditoriumId,
                                               @Valid @RequestBody AuditoriumCreateRequest req,
                                               @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword) {
        if (!isAdmin(headerPassword)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "admin password required"));
        }
        String showId = ShowIdGenerator.fromShowName(req.showName());
        try {
            Auditorium a = auditoriumService.update(auditoriumId, showId, req.showName(), req.auditoriumName(), req.showDate(), req.showTime(), req.ticketAmount(), req.totalSeats(), req.reservedSeats());
            return ResponseEntity.ok(a);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Patch an auditorium by id. Admin header required.
     */
    @PatchMapping("/{auditoriumId}")
    public ResponseEntity<?> patchAuditorium(@PathVariable String auditoriumId,
                                             @RequestBody Map<String, Object> patch,
                                             @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword) {
        if (!isAdmin(headerPassword)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "admin password required"));
        }
        try {
            String auditoriumName = patch.containsKey("auditoriumName") ? String.valueOf(patch.get("auditoriumName")) : null;
            String showName = patch.containsKey("showName") ? String.valueOf(patch.get("showName")) : null;
            LocalDate showDate = patch.containsKey("showDate") ? LocalDate.parse(String.valueOf(patch.get("showDate"))) : null;
            LocalTime showTime = patch.containsKey("showTime") ? LocalTime.parse(String.valueOf(patch.get("showTime"))) : null;
            BigDecimal ticketAmount = patch.containsKey("ticketAmount") ? new BigDecimal(String.valueOf(patch.get("ticketAmount"))) : null;
            Integer totalSeats = patch.containsKey("totalSeats") ? Integer.parseInt(String.valueOf(patch.get("totalSeats"))) : null;
            Integer reservedSeats = patch.containsKey("reservedSeats") ? Integer.parseInt(String.valueOf(patch.get("reservedSeats"))) : null;

            // If showName is patched, recompute showId; otherwise keep null (service keeps current)
            String showId = null;
            if (showName != null && !showName.isBlank()) {
                showId = ShowIdGenerator.fromShowName(showName);
            }

            Auditorium updated = auditoriumService.patch(auditoriumId, showId, showName, auditoriumName, showDate, showTime, ticketAmount, totalSeats, reservedSeats);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Delete an auditorium by id. Admin header required. */
    @DeleteMapping("/{auditoriumId}")
    public ResponseEntity<?> deleteAuditorium(@PathVariable String auditoriumId,
                                              @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword) {
        if (!isAdmin(headerPassword)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "admin password required"));
        }
        try {
            auditoriumService.delete(auditoriumId);
            return ResponseEntity.ok(Map.of("message", "Auditorium deleted", "auditoriumId", auditoriumId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    public record AuditoriumCreateRequest(
            @NotBlank String auditoriumName,
            @NotBlank String showName,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate showDate,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime showTime,
            @NotNull BigDecimal ticketAmount,
            @Min(1) int totalSeats,
            @Min(0) int reservedSeats
    ) {}
}
