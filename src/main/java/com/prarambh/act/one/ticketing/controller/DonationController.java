package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Donation;
import com.prarambh.act.one.ticketing.service.DonationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DonationController {

    private final DonationService service;

    @Value("${actone.admin.purge-password:}")
    private String adminPassword;

    public DonationController(DonationService service) { this.service = service; }

    private boolean isAdmin(String pass){
        return StringUtils.hasText(adminPassword) && StringUtils.hasText(pass) && pass.equals(adminPassword);
    }

    @PostMapping("/api/donations")
    public ResponseEntity<?> create(@RequestBody Donation d){
        try{
            Donation saved = service.create(d);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        }catch(IllegalArgumentException e){
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/api/donations")
    public List<Donation> list(){ return service.listAll(); }

    @GetMapping("/api/donations/{serial}")
    public ResponseEntity<?> getBySerial(@PathVariable String serial){
        Optional<Donation> d = service.findBySerial(serial);
        return d.<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Replace a donation by DB id. Admin header required. */
    @PutMapping("/api/donations/{id}")
    public ResponseEntity<?> replace(@PathVariable Long id,
                                     @RequestBody Donation in,
                                     @RequestHeader(name = "X-Admin-Password", required = false) String pass) {
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "admin password required"));
        try {
            return ResponseEntity.ok(service.replace(id, in));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** Patch a donation by DB id. Admin header required. */
    @PatchMapping("/api/donations/{id}")
    public ResponseEntity<?> patch(@PathVariable Long id,
                                   @RequestBody Map<String, Object> patch,
                                   @RequestHeader(name = "X-Admin-Password", required = false) String pass) {
        if (!isAdmin(pass)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "admin password required"));
        try {
            return ResponseEntity.ok(service.patch(id, patch));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/api/donations/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!StringUtils.hasText(pass) || !pass.equals(adminPassword)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message","admin password required"));
        service.deleteById(id);
        return ResponseEntity.ok(Map.of("message","deleted","id", id));
    }
}
