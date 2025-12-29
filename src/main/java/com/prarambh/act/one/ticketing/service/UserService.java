package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.User;
import com.prarambh.act.one.ticketing.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private final UserRepository repo;

    public UserService(UserRepository repo) {
        this.repo = repo;
    }

    private String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Transactional
    public User createUser(User u) {
        if (u == null) throw new IllegalArgumentException("user required");
        if (!StringUtils.hasText(u.getFullName())) throw new IllegalArgumentException("fullName required");
        if (StringUtils.hasText(u.getPhoneNumber())){
            String digits = u.getPhoneNumber().replaceAll("\\D", "");
            if (digits.length() > 10) digits = digits.substring(digits.length()-10);
            u.setPhoneNumber(digits);
        }
        if (StringUtils.hasText(u.getEmail())) u.setEmail(u.getEmail().trim());

        if (repo.existsByFullNameAndPhoneNumberAndEmail(u.getFullName(), u.getPhoneNumber(), u.getEmail())) {
            throw new IllegalArgumentException("duplicate user");
        }

        u.setUserId(shortUuid());
        return repo.save(u);
    }

    @Transactional
    public User updateUser(Long id, User update) {
        User existing = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("user not found"));
        if (update == null) throw new IllegalArgumentException("update payload required");
        if (StringUtils.hasText(update.getFullName())) existing.setFullName(update.getFullName());
        if (StringUtils.hasText(update.getPhoneNumber())){
            String digits = update.getPhoneNumber().replaceAll("\\D", "");
            if (digits.length() > 10) digits = digits.substring(digits.length()-10);
            existing.setPhoneNumber(digits);
        }
        if (StringUtils.hasText(update.getEmail())) existing.setEmail(update.getEmail().trim());

        User match = repo.findFirstByFullNameAndPhoneNumberAndEmail(existing.getFullName(), existing.getPhoneNumber(), existing.getEmail());
        if (match != null && !match.getId().equals(id)) {
            throw new IllegalArgumentException("duplicate user");
        }

        return repo.save(existing);
    }

    public List<User> listAll(){ return repo.findAll(); }

    public User findByUserId(String userId){
        if (!StringUtils.hasText(userId)) return null;
        return repo.findByUserId(userId.trim());
    }

    public List<User> findByPhone(String phone){
        if (!StringUtils.hasText(phone)) return List.of();
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() > 10) digits = digits.substring(digits.length()-10);
        return repo.findByPhoneNumberEndingWith(digits);
    }

    public List<User> findByName(String name){
        if (!StringUtils.hasText(name)) return List.of();
        return repo.findByFullNameContainingIgnoreCase(name.trim());
    }

    public List<User> findByEmail(String email){
        if (!StringUtils.hasText(email)) return List.of();
        return repo.findByEmailIgnoreCase(email.trim());
    }

    @Transactional
    public void deleteById(Long id){ repo.deleteById(id); }

    /**
     * Ensure a User row exists for the given userId. If not present, create one using the
     * provided fields (normalizing phone/email like createUser). Returns the persisted User.
     */
    @Transactional
    public User ensureUserForTicket(String userId, String fullName, String phone, String email) {
        if (!StringUtils.hasText(userId)) throw new IllegalArgumentException("userId required");
        User existing = repo.findByUserId(userId);
        if (existing != null) return existing;

        User u = new User();
        u.setUserId(userId);
        u.setFullName(fullName == null ? "" : fullName.trim());
        if (StringUtils.hasText(phone)){
            String digits = phone.replaceAll("\\D", "");
            if (digits.length() > 10) digits = digits.substring(digits.length()-10);
            u.setPhoneNumber(digits);
        }
        if (StringUtils.hasText(email)) u.setEmail(email.trim());

        // Do not enforce uniqueness here by throwing; try-save and let DB constraints fail in the unlikely collision.
        return repo.save(u);
    }
}
