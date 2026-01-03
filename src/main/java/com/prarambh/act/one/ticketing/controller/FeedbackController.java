package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Feedback;
import com.prarambh.act.one.ticketing.service.FeedbackService;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Value("${actone.admin.purge-password:}")
    private String adminPassword;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    private boolean isAdmin(String pass){
        return StringUtils.hasText(adminPassword) && StringUtils.hasText(pass) && pass.equals(adminPassword);
    }

    @PostMapping
    public ResponseEntity<?> submit(@RequestBody Feedback f) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.submit(f));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(name = "X-Admin-Password", required = false) String pass) {
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","admin password required"));
        return ResponseEntity.ok(feedbackService.listAll());
    }

    /** Get a single feedback record by id. Admin header required. */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id,
                                     @RequestHeader(name = "X-Admin-Password", required = false) String pass) {
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","admin password required"));
        Optional<Feedback> f = feedbackService.findById(id);
        return f.<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Replace a feedback record by id. Admin header required. */
    @PutMapping("/{id}")
    public ResponseEntity<?> replace(@PathVariable Long id,
                                     @RequestBody Feedback in,
                                     @RequestHeader(name = "X-Admin-Password", required = false) String pass) {
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","admin password required"));
        try {
            return ResponseEntity.ok(feedbackService.replace(id, in));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Patch a feedback record by id. Admin header required. */
    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(@PathVariable Long id,
                                   @RequestBody Map<String, Object> patch,
                                   @RequestHeader(name = "X-Admin-Password", required = false) String pass) {
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","admin password required"));
        try {
            return ResponseEntity.ok(feedbackService.patch(id, patch));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                    @RequestHeader(name = "X-Admin-Password", required = false) String pass) {
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","admin password required"));
        feedbackService.deleteById(id);
        return ResponseEntity.ok(Map.of("message","deleted"));
    }

    /**
     * Replace feedback records matched by user identity fields.
     *
     * <p>Since Feedback doesn't store {@code userId}, this endpoint targets rows using
     * one (or more) of: {@code email}, {@code phoneNumber}, {@code fullName}.
     * Admin header required.
     */
    @PutMapping("/by-user")
    public ResponseEntity<?> replaceByUser(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String fullName,
            @RequestBody Feedback in,
            @RequestHeader(name = "X-Admin-Password", required = false) String pass
    ) {
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","admin password required"));

        if (!StringUtils.hasText(email) && !StringUtils.hasText(phoneNumber) && !StringUtils.hasText(fullName)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Provide at least one of email, phoneNumber, fullName"));
        }

        try {
            return ResponseEntity.ok(feedbackService.replaceByUser(email, phoneNumber, fullName, in));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Patch feedback records matched by user identity fields.
     * Admin header required.
     */
    @PatchMapping("/by-user")
    public ResponseEntity<?> patchByUser(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String fullName,
            @RequestBody Map<String, Object> patch,
            @RequestHeader(name = "X-Admin-Password", required = false) String pass
    ) {
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","admin password required"));

        if (!StringUtils.hasText(email) && !StringUtils.hasText(phoneNumber) && !StringUtils.hasText(fullName)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Provide at least one of email, phoneNumber, fullName"));
        }

        try {
            return ResponseEntity.ok(feedbackService.patchByUser(email, phoneNumber, fullName, patch));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Delete feedback records matched by user identity fields.
     * Admin header required.
     */
    @DeleteMapping("/by-user")
    public ResponseEntity<?> deleteByUser(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String fullName,
            @RequestHeader(name = "X-Admin-Password", required = false) String pass
    ) {
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","admin password required"));

        if (!StringUtils.hasText(email) && !StringUtils.hasText(phoneNumber) && !StringUtils.hasText(fullName)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Provide at least one of email, phoneNumber, fullName"));
        }

        int deleted = feedbackService.deleteByUser(email, phoneNumber, fullName);
        return ResponseEntity.ok(Map.of("message", "deleted", "deletedCount", deleted));
    }
}
