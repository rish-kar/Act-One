package com.prarambh.act.one.ticketing.repository;

import com.prarambh.act.one.ticketing.model.Donation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    Optional<Donation> findBySerialNumber(String serial);
}

