package com.prarambh.act.one.ticketing.repository;

import com.prarambh.act.one.ticketing.model.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUserId(String userId);
    List<User> findByPhoneNumberEndingWith(String phone);
    List<User> findByFullNameContainingIgnoreCase(String name);
    List<User> findByEmailIgnoreCase(String email);
    boolean existsByFullNameAndPhoneNumberAndEmail(String fullName, String phone, String email);
    User findFirstByFullNameAndPhoneNumberAndEmail(String fullName, String phone, String email);
}

