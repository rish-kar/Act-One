package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Feedback;
import com.prarambh.act.one.ticketing.repository.FeedbackRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository repo;

    @Transactional
    public Feedback submit(Feedback f) {
        if (f == null) throw new IllegalArgumentException("feedback required");
        if (f.getFullName() == null || f.getFullName().isBlank()) throw new IllegalArgumentException("fullName required");
        if (f.getMessage() == null || f.getMessage().isBlank()) throw new IllegalArgumentException("message required");
        if (f.getPhoneNumber() != null) {
            String digits = f.getPhoneNumber().replaceAll("\\D", "");
            if (digits.length() > 10) digits = digits.substring(digits.length()-10);
            f.setPhoneNumber(digits);
        }
        if (f.getEmail() != null) f.setEmail(f.getEmail().trim());
        return repo.save(f);
    }

    public List<Feedback> listAll() { return repo.findAll(); }

    @Transactional
    public void deleteById(Long id) { repo.deleteById(id); }
}

