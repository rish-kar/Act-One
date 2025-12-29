package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Auditorium;
import com.prarambh.act.one.ticketing.service.AuditoriumService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auditoriums")
public class AuditoriumController {

    private final AuditoriumService auditoriumService;
    private final String adminPassword = System.getenv().getOrDefault("ADMIN_PASSWORD","prarambh-admin-delhi");

    public AuditoriumController(AuditoriumService auditoriumService) {
        this.auditoriumService = auditoriumService;
    }

    private boolean isAdmin(String pass){ return StringUtils.hasText(pass) && pass.equals(adminPassword); }

    public record UpsertAuditoriumRequest(
            String showId,
            String auditoriumName,
            LocalDate showDate,
            LocalTime showTime,
            Integer totalSeats,
            Integer reservedSeats
    ) {}

    public record PatchAuditoriumRequest(
            String showId,
            String auditoriumName,
            LocalDate showDate,
            LocalTime showTime,
            Integer totalSeats,
            Integer reservedSeats
    ) {}

    @PostMapping
    public ResponseEntity<?> upsert(@RequestBody UpsertAuditoriumRequest req,
                                   @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        try {
            Auditorium a = auditoriumService.upsert(
                    req.showId(),
                    req.auditoriumName(),
                    req.showDate(),
                    req.showTime(),
                    req.totalSeats() == null ? 0 : req.totalSeats(),
                    req.reservedSeats() == null ? 0 : req.reservedSeats()
            );
            return ResponseEntity.ok(a);
        } catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Full replace update. Derived seat fields are recalculated automatically. */
    @PutMapping("/{auditoriumId}")
    public ResponseEntity<?> putUpdate(@PathVariable String auditoriumId,
                                       @RequestBody UpsertAuditoriumRequest req,
                                       @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        try {
            Auditorium a = auditoriumService.update(
                    auditoriumId,
                    req.showId(),
                    req.auditoriumName(),
                    req.showDate(),
                    req.showTime(),
                    req.totalSeats() == null ? 0 : req.totalSeats(),
                    req.reservedSeats() == null ? 0 : req.reservedSeats()
            );
            return ResponseEntity.ok(a);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "bad request" : e.getMessage();
            if (msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("message", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("message", msg));
        }
    }

    /** Partial update. Derived seat fields are recalculated automatically. */
    @PatchMapping("/{auditoriumId}")
    public ResponseEntity<?> patchUpdate(@PathVariable String auditoriumId,
                                         @RequestBody PatchAuditoriumRequest req,
                                         @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        try {
            Auditorium a = auditoriumService.patch(
                    auditoriumId,
                    req.showId(),
                    req.auditoriumName(),
                    req.showDate(),
                    req.showTime(),
                    req.totalSeats(),
                    req.reservedSeats()
            );
            return ResponseEntity.ok(a);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "bad request" : e.getMessage();
            if (msg.toLowerCase().contains("not found")) {
                return ResponseEntity.status(404).body(Map.of("message", msg));
            }
            return ResponseEntity.badRequest().body(Map.of("message", msg));
        }
    }

    @DeleteMapping("/{auditoriumId}")
    public ResponseEntity<?> delete(@PathVariable String auditoriumId,
                                    @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        auditoriumService.delete(auditoriumId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{auditoriumId}/recalculate")
    public ResponseEntity<?> recalc(@PathVariable String auditoriumId,
                                   @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        try {
            return ResponseEntity.ok(auditoriumService.recalc(auditoriumId));
        } catch (IllegalArgumentException e){
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{auditoriumId}")
    public ResponseEntity<?> get(@PathVariable String auditoriumId,
                                 @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        return auditoriumService.findById(auditoriumId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("message","auditorium not found")));
    }

    @GetMapping("/{auditoriumId}/total-seats")
    public ResponseEntity<?> totalSeats(@PathVariable String auditoriumId,
                                        @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        return auditoriumService.findById(auditoriumId)
                .<ResponseEntity<?>>map(a -> ResponseEntity.ok(Map.of("auditoriumId", a.getAuditoriumId(), "totalSeats", a.getTotalSeats())))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("message","auditorium not found")));
    }

    @GetMapping("/{auditoriumId}/available-seats")
    public ResponseEntity<?> availableSeats(@PathVariable String auditoriumId,
                                            @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        return auditoriumService.findById(auditoriumId)
                .<ResponseEntity<?>>map(a -> ResponseEntity.ok(Map.of("auditoriumId", a.getAuditoriumId(), "availableSeats", a.getAvailableSeats())))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("message","auditorium not found")));
    }
}
