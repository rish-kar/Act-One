package com.prarambh.act.one.ticketing.repository;

import com.prarambh.act.one.ticketing.model.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByUserId(String userId);
    boolean existsByUserId(String userId);
    List<User> findByPhoneNumberEndingWith(String phone);
    List<User> findByFullNameContainingIgnoreCase(String name);
    List<User> findByEmailIgnoreCase(String email);
    boolean existsByFullNameAndPhoneNumberAndEmail(String fullName, String phone, String email);

    // Case-insensitive email matching for identity lookup
    @Query("SELECT u FROM User u WHERE u.fullName = :fullName AND u.phoneNumber = :phone AND LOWER(u.email) = LOWER(:email)")
    User findFirstByFullNameAndPhoneNumberAndEmailIgnoreCase(@Param("fullName") String fullName, @Param("phone") String phone, @Param("email") String email);

    User findFirstByFullNameAndPhoneNumberAndEmail(String fullName, String phone, String email);
}
