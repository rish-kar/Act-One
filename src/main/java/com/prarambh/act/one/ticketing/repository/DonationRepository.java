package com.prarambh.act.one.ticketing.repository;

import com.prarambh.act.one.ticketing.model.Donation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    List<Donation> findByPhoneNumberEndingWith(String last10Digits);
    List<Donation> findByEmailIgnoreCase(String email);
    List<Donation> findByFullNameContainingIgnoreCase(String fullName);

    // Helpful method to fetch in stable order for serial number management
    List<Donation> findAllByOrderBySerialNumberAsc();

    Donation findBySerialNumber(String serialNumber);
}
