package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Donation;
import com.prarambh.act.one.ticketing.repository.DonationRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to manage donations. Generates a unique 10-character serial per donation.
 */
@Service
public class DonationService {

    private final DonationRepository repo;
    private final SecureRandom rnd = new SecureRandom();

    public DonationService(DonationRepository repo) {
        this.repo = repo;
    }

    /**
     * Generate a unique 10-character serial number for a donation.
     *
     * @return unique serial
     */
    private String genSerial() {
        // generate 10 char alnum
        String s;
        do {
            byte[] b = new byte[8];
            rnd.nextBytes(b);
            s = Base64.getUrlEncoder().withoutPadding().encodeToString(b).substring(0, 10).toUpperCase();
        } while (repo.findBySerialNumber(s).isPresent());
        return s;
    }

    /**
     * Create a donation record. Serial is auto-generated.
     *
     * @param d donation payload
     * @return persisted donation
     */
    @Transactional
    public Donation create(Donation d) {
        if (d == null) throw new IllegalArgumentException("donation required");
        if (d.getAmount() == null) throw new IllegalArgumentException("amount required");
        if (d.getAmount().doubleValue() <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (d.getFullName() == null || d.getFullName().isBlank()) throw new IllegalArgumentException("fullName required");
        if (d.getTransactionId() == null || d.getTransactionId().isBlank()) throw new IllegalArgumentException("transactionId required");

        d.setSerialNumber(genSerial());
        return repo.save(d);
    }

    /**
     * List all donations.
     */
    public List<Donation> listAll(){ return repo.findAll(); }

    /**
     * Find donation by serial number.
     */
    public Optional<Donation> findBySerial(String serial){ return repo.findBySerialNumber(serial); }

    /**
     * Delete donation by DB id.
     */
    @Transactional
    public void deleteById(Long id){ repo.deleteById(id); }

    /**
     * Replace a donation by database id. Serial number is preserved.
     */
    @Transactional
    public Donation replace(Long id, Donation in) {
        if (id == null) throw new IllegalArgumentException("id required");
        Donation existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("donation not found"));
        if (in == null) throw new IllegalArgumentException("donation required");
        if (in.getAmount() == null) throw new IllegalArgumentException("amount required");
        if (in.getAmount().doubleValue() <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (in.getFullName() == null || in.getFullName().isBlank()) throw new IllegalArgumentException("fullName required");
        if (in.getTransactionId() == null || in.getTransactionId().isBlank()) throw new IllegalArgumentException("transactionId required");

        existing.setFullName(in.getFullName());
        existing.setPhoneNumber(in.getPhoneNumber());
        existing.setEmail(in.getEmail());
        existing.setMessage(in.getMessage());
        existing.setTransactionId(in.getTransactionId());
        existing.setAmount(in.getAmount());
        return repo.save(existing);
    }

    /**
     * Patch a donation by database id.
     */
    @Transactional
    public Donation patch(Long id, Map<String, Object> patch) {
        if (id == null) throw new IllegalArgumentException("id required");
        Donation existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("donation not found"));
        if (patch == null || patch.isEmpty()) return existing;

        if (patch.containsKey("fullName")) existing.setFullName(String.valueOf(patch.get("fullName")));
        if (patch.containsKey("phoneNumber")) existing.setPhoneNumber(String.valueOf(patch.get("phoneNumber")));
        if (patch.containsKey("email")) existing.setEmail(String.valueOf(patch.get("email")));
        if (patch.containsKey("message")) existing.setMessage(String.valueOf(patch.get("message")));
        if (patch.containsKey("transactionId")) existing.setTransactionId(String.valueOf(patch.get("transactionId")));
        if (patch.containsKey("amount")) existing.setAmount(new java.math.BigDecimal(String.valueOf(patch.get("amount"))));

        if (existing.getFullName() == null || existing.getFullName().isBlank()) throw new IllegalArgumentException("fullName required");
        if (existing.getTransactionId() == null || existing.getTransactionId().isBlank()) throw new IllegalArgumentException("transactionId required");
        if (existing.getAmount() == null) throw new IllegalArgumentException("amount required");
        if (existing.getAmount().doubleValue() <= 0) throw new IllegalArgumentException("amount must be > 0");

        return repo.save(existing);
    }
}
