package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Donation;
import com.prarambh.act.one.ticketing.service.DonationService;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DonationController {

    private final DonationService service;
    private final String adminPassword = System.getenv().getOrDefault("ADMIN_PASSWORD","prarambh-admin-delhi");

    public DonationController(DonationService service) { this.service = service; }

    @PostMapping("/api/donations")
    public ResponseEntity<?> create(@RequestBody Donation d){
        try{
            Donation saved = service.create(d);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        }catch(IllegalArgumentException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/donations")
    public List<Donation> list(){ return service.listAll(); }

    @GetMapping("/api/donations/{serial}")
    public ResponseEntity<?> getBySerial(@PathVariable String serial){
        Optional<Donation> d = service.findBySerial(serial);
        return d.<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/donations/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, @RequestHeader(name = "X-Admin-Password", required = false) String pass){
        if (!StringUtils.hasText(pass) || !pass.equals(adminPassword)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("admin password required");
        service.deleteById(id);
        return ResponseEntity.ok().build();
    }
}

