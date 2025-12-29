package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Donation;
import com.prarambh.act.one.ticketing.repository.DonationRepository;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DonationService {

    private final DonationRepository repo;
    private final SecureRandom rnd = new SecureRandom();

    public DonationService(DonationRepository repo) {
        this.repo = repo;
    }

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

    @Transactional
    public Donation create(Donation d) {
        if (d == null) throw new IllegalArgumentException("donation required");
        if (d.getAmount() == null) throw new IllegalArgumentException("amount required");
        if (d.getAmount().doubleValue() <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (d.getFullName() == null || d.getFullName().isBlank()) throw new IllegalArgumentException("fullName required");

        d.setSerialNumber(genSerial());
        return repo.save(d);
    }

    public List<Donation> listAll(){ return repo.findAll(); }

    public Optional<Donation> findBySerial(String serial){ return repo.findBySerialNumber(serial); }

    @Transactional
    public void deleteById(Long id){ repo.deleteById(id); }
}

