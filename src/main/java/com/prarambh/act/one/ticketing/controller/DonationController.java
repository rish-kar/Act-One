package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Donation;
import com.prarambh.act.one.ticketing.service.DonationService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/donations")
public class DonationController {

    private final DonationService donationService;

    public DonationController(DonationService donationService) {
        this.donationService = donationService;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Donation donation) {
        try {
            Donation saved = donationService.createDonation(donation);
            URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.getId()).toUri();
            return ResponseEntity.created(uri).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public List<Donation> listAll() {
        return donationService.listAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return donationService.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-phone")
    public List<Donation> byPhone(@RequestParam String phoneNumber) {
        return donationService.findByPhone(phoneNumber);
    }

    @GetMapping("/by-email")
    public List<Donation> byEmail(@RequestParam String email) {
        return donationService.findByEmail(email);
    }

    @GetMapping("/by-name")
    public List<Donation> byName(@RequestParam String fullName) {
        return donationService.findByFullName(fullName);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        donationService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

