package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.User;
import com.prarambh.act.one.ticketing.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Service for managing User records that correspond to ticket purchasers.
 *
 * <p>Normalizes input (trims email, keeps only last-10 digits of phone) and enforces uniqueness
 * of the composite (fullName, phoneNumber, email) when creating/updating users.
 */
@Service
public class UserService {

    private final UserRepository repo;

    public UserService(UserRepository repo) {
        this.repo = repo;
    }

    private String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * Create a new user in the repository.
     *
     * @param u user payload; must contain fullName and non-null fields will be normalized
     * @return persisted user entity
     * @throws IllegalArgumentException when payload is invalid or duplicate
     */
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

    /**
     * Update an existing user record.
     *
     * @param id database id of the user to update
     * @param update payload containing fields to change
     * @return updated user entity
     */
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

    /**
     * List all users.
     *
     * @return list of users.
     */
    public List<User> listAll(){ return repo.findAll(); }

    /**
     * Find a user by their short userId.
     *
     * @param userId short user id
     * @return matching user or null
     */
    public User findByUserId(String userId){
        if (!StringUtils.hasText(userId)) return null;
        return repo.findByUserId(userId.trim());
    }

    /**
     * Find users matching a phone suffix (last-10 digits).
     *
     * @param phone phone string
     * @return list of matching users
     */
    public List<User> findByPhone(String phone){
        if (!StringUtils.hasText(phone)) return List.of();
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() > 10) digits = digits.substring(digits.length()-10);
        return repo.findByPhoneNumberEndingWith(digits);
    }

    /**
     * Find users by (partial) full name.
     *
     * @param name query
     * @return matching users
     */
    public List<User> findByName(String name){
        if (!StringUtils.hasText(name)) return List.of();
        return repo.findByFullNameContainingIgnoreCase(name.trim());
    }

    /**
     * Find users by email.
     *
     * @param email email address
     * @return matching users
     */
    public List<User> findByEmail(String email){
        if (!StringUtils.hasText(email)) return List.of();
        return repo.findByEmailIgnoreCase(email.trim());
    }

    /**
     * Delete a user by database id.
     *
     * @param id db id
     */
    @Transactional
    public void deleteById(Long id){ repo.deleteById(id); }

    /**
     * Ensure a User row exists for the given userId. If not present, create one using the
     * provided fields (normalizing phone/email like createUser). Returns the persisted User.
     *
     * @param userId short user id (non-empty)
     * @param fullName full name to store when creating
     * @param phone phone number; normalization is applied
     * @param email email address to store when creating
     * @return existing or newly-created User
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
