package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Feedback;
import com.prarambh.act.one.ticketing.service.FeedbackService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final String adminPassword = System.getenv().getOrDefault("ADMIN_PASSWORD","prarambh-admin-delhi");

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    private boolean isAdmin(String pass){ return StringUtils.hasText(pass) && pass.equals(adminPassword); }

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
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        return ResponseEntity.ok(feedbackService.listAll());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
                                    @RequestHeader(name = "X-Admin-Password", required = false) String pass) {
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        feedbackService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}

