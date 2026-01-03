package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Feedback;
import com.prarambh.act.one.ticketing.repository.FeedbackRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to submit and list feedback messages.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository repo;

    /**
     * Submit a feedback entry. Phone number and email are normalized.
     *
     * @param f feedback payload
     * @return persisted feedback
     */
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

    /**
     * List all feedback entries.
     *
     * @return list of feedback
     */
    public List<Feedback> listAll() { return repo.findAll(); }

    /**
     * Delete by database id.
     *
     * @param id db id
     */
    @Transactional
    public void deleteById(Long id) { repo.deleteById(id); }

    /**
     * Find a feedback record by database id.
     *
     * @param id database id
     * @return optional feedback
     */
    public Optional<Feedback> findById(Long id) {
        if (id == null) return Optional.empty();
        return repo.findById(id);
    }

    /**
     * Replace a feedback record by database id.
     *
     * @param id database id
     * @param in replacement payload
     * @return updated feedback
     */
    @Transactional
    public Feedback replace(Long id, Feedback in) {
        if (id == null) throw new IllegalArgumentException("id required");
        Feedback existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("feedback not found"));
        if (in == null) throw new IllegalArgumentException("feedback required");
        if (in.getFullName() == null || in.getFullName().isBlank()) throw new IllegalArgumentException("fullName required");
        if (in.getMessage() == null || in.getMessage().isBlank()) throw new IllegalArgumentException("message required");

        existing.setFullName(in.getFullName());
        existing.setPhoneNumber(in.getPhoneNumber());
        existing.setEmail(in.getEmail());
        existing.setMessage(in.getMessage());

        // normalize like submit()
        if (existing.getPhoneNumber() != null) {
            String digits = existing.getPhoneNumber().replaceAll("\\D", "");
            if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
            existing.setPhoneNumber(digits);
        }
        if (existing.getEmail() != null) existing.setEmail(existing.getEmail().trim());

        return repo.save(existing);
    }

    /**
     * Patch a feedback record by database id.
     *
     * @param id database id
     * @param patch partial update map (fullName/phoneNumber/email/message)
     * @return updated feedback
     */
    @Transactional
    public Feedback patch(Long id, Map<String, Object> patch) {
        if (id == null) throw new IllegalArgumentException("id required");
        Feedback existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("feedback not found"));
        if (patch == null || patch.isEmpty()) return existing;

        if (patch.containsKey("fullName")) existing.setFullName(String.valueOf(patch.get("fullName")));
        if (patch.containsKey("phoneNumber")) existing.setPhoneNumber(String.valueOf(patch.get("phoneNumber")));
        if (patch.containsKey("email")) existing.setEmail(String.valueOf(patch.get("email")));
        if (patch.containsKey("message")) existing.setMessage(String.valueOf(patch.get("message")));

        if (existing.getFullName() == null || existing.getFullName().isBlank()) throw new IllegalArgumentException("fullName required");
        if (existing.getMessage() == null || existing.getMessage().isBlank()) throw new IllegalArgumentException("message required");

        // normalize like submit()
        if (existing.getPhoneNumber() != null) {
            String digits = existing.getPhoneNumber().replaceAll("\\D", "");
            if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
            existing.setPhoneNumber(digits);
        }
        if (existing.getEmail() != null) existing.setEmail(existing.getEmail().trim());

        return repo.save(existing);
    }

    /**
     * Replace all feedback rows matched by user identity.
     *
     * <p>At least one of email/phoneNumber/fullName must be provided.
     */
    @Transactional
    public List<Feedback> replaceByUser(String email, String phoneNumber, String fullName, Feedback in) {
        if ((email == null || email.isBlank()) && (phoneNumber == null || phoneNumber.isBlank()) && (fullName == null || fullName.isBlank())) {
            throw new IllegalArgumentException("user selector required");
        }
        if (in == null) throw new IllegalArgumentException("feedback required");

        List<Feedback> matches = findByUserSelector(email, phoneNumber, fullName);
        if (matches.isEmpty()) throw new IllegalArgumentException("no matching feedback records");

        for (Feedback f : matches) {
            f.setFullName(in.getFullName());
            f.setPhoneNumber(in.getPhoneNumber());
            f.setEmail(in.getEmail());
            f.setMessage(in.getMessage());

            // validate/normalize using existing logic
            submit(f);
        }
        return repo.saveAll(matches);
    }

    /**
     * Patch all feedback rows matched by user identity.
     */
    @Transactional
    public List<Feedback> patchByUser(String email, String phoneNumber, String fullName, Map<String, Object> patch) {
        if ((email == null || email.isBlank()) && (phoneNumber == null || phoneNumber.isBlank()) && (fullName == null || fullName.isBlank())) {
            throw new IllegalArgumentException("user selector required");
        }
        List<Feedback> matches = findByUserSelector(email, phoneNumber, fullName);
        if (matches.isEmpty()) throw new IllegalArgumentException("no matching feedback records");
        if (patch == null || patch.isEmpty()) return matches;

        for (Feedback f : matches) {
            if (patch.containsKey("fullName")) f.setFullName(String.valueOf(patch.get("fullName")));
            if (patch.containsKey("phoneNumber")) f.setPhoneNumber(String.valueOf(patch.get("phoneNumber")));
            if (patch.containsKey("email")) f.setEmail(String.valueOf(patch.get("email")));
            if (patch.containsKey("message")) f.setMessage(String.valueOf(patch.get("message")));

            // validate/normalize using existing logic
            submit(f);
        }
        return repo.saveAll(matches);
    }

    /**
     * Delete all feedback rows matched by user identity.
     *
     * @return deleted row count
     */
    @Transactional
    public int deleteByUser(String email, String phoneNumber, String fullName) {
        if ((email == null || email.isBlank()) && (phoneNumber == null || phoneNumber.isBlank()) && (fullName == null || fullName.isBlank())) {
            throw new IllegalArgumentException("user selector required");
        }
        List<Feedback> matches = findByUserSelector(email, phoneNumber, fullName);
        int count = matches.size();
        repo.deleteAllInBatch(matches);
        return count;
    }

    private List<Feedback> findByUserSelector(String email, String phoneNumber, String fullName) {
        // Minimal implementation: in-memory filter over all rows.
        // Feedback volume is expected to be small; keeps changes minimal (no new repository queries).
        String emailNorm = email == null ? null : email.trim();
        String phoneDigits = phoneNumber == null ? null : phoneNumber.replaceAll("\\D", "");
        String nameNorm = fullName == null ? null : fullName.trim();

        return repo.findAll().stream()
                .filter(f -> {
                    boolean ok = true;
                    if (emailNorm != null && !emailNorm.isBlank()) {
                        ok = ok && f.getEmail() != null && f.getEmail().equalsIgnoreCase(emailNorm);
                    }
                    if (phoneDigits != null && !phoneDigits.isBlank()) {
                        String fDigits = f.getPhoneNumber() == null ? "" : f.getPhoneNumber().replaceAll("\\D", "");
                        ok = ok && !fDigits.isBlank() && fDigits.endsWith(phoneDigits.length() > 10 ? phoneDigits.substring(phoneDigits.length() - 10) : phoneDigits);
                    }
                    if (nameNorm != null && !nameNorm.isBlank()) {
                        ok = ok && f.getFullName() != null && f.getFullName().toLowerCase().contains(nameNorm.toLowerCase());
                    }
                    return ok;
                })
                .toList();
    }
}
