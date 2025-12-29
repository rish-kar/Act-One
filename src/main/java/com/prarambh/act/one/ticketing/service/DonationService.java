package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Donation;
import com.prarambh.act.one.ticketing.repository.DonationRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DonationService {

    private final DonationRepository donationRepository;
    private final SecureRandom random = new SecureRandom();

    public DonationService(DonationRepository donationRepository) {
        this.donationRepository = donationRepository;
    }

    private String generateSerial10() {
        // generate a 10-digit numeric string; ensure uniqueness by checking repository
        final int MAX_ATTEMPTS = 10000; // high limit to avoid infinite loop
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            long v = Math.abs(random.nextLong()) % 1_000_000_0000L; // 0 .. 9,999,999,999
            String s = String.format("%010d", v);
            if (donationRepository.findBySerialNumber(s) == null) return s;
        }
        // If we unexpectedly couldn't find a unique value, fail loudly so the caller can retry or alert
        throw new IllegalStateException("Unable to generate unique donation serial after " + MAX_ATTEMPTS + " attempts");
    }

    @Transactional
    public Donation createDonation(Donation d) {
        if (d == null) throw new IllegalArgumentException("donation must be provided");
        if (!StringUtils.hasText(d.getFullName())) throw new IllegalArgumentException("fullName is required");
        if (d.getAmount() == null) throw new IllegalArgumentException("amount is required");
        if (d.getAmount().compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("amount must be positive");

        // Normalize phone: store last 10 digits only if present
        if (StringUtils.hasText(d.getPhoneNumber())) {
            String digits = d.getPhoneNumber().replaceAll("\\D", "");
            if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
            d.setPhoneNumber(digits);
        }

        // Normalize email: lower-case
        if (StringUtils.hasText(d.getEmail())) d.setEmail(d.getEmail().trim());

        // Generate unique 10-digit serial identifier and set it
        d.setSerialNumber(generateSerial10());

        return donationRepository.save(d);
    }

    public List<Donation> listAll() {
        return donationRepository.findAll();
    }

    public Optional<Donation> findById(Long id) {
        return donationRepository.findById(id);
    }

    @Transactional
    public void deleteById(Long id) {
        donationRepository.deleteById(id);
    }

    public List<Donation> findByPhone(String phone) {
        if (!StringUtils.hasText(phone)) return List.of();
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
        return donationRepository.findByPhoneNumberEndingWith(digits);
    }

    public List<Donation> findByEmail(String email) {
        if (!StringUtils.hasText(email)) return List.of();
        return donationRepository.findByEmailIgnoreCase(email.trim());
    }

    public List<Donation> findByFullName(String name) {
        if (!StringUtils.hasText(name)) return List.of();
        return donationRepository.findByFullNameContainingIgnoreCase(name.trim());
    }

    public Donation findBySerial(String serial) {
        if (!StringUtils.hasText(serial)) return null;
        return donationRepository.findBySerialNumber(serial.trim());
    }
}
