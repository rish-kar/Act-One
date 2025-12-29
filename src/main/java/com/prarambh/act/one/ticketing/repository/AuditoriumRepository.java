package com.prarambh.act.one.ticketing.repository;

import com.prarambh.act.one.ticketing.model.Auditorium;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriumRepository extends JpaRepository<Auditorium, String> {
    Optional<Auditorium> findFirstByShowId(String showId);
}
