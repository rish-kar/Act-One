package com.prarambh.act.one.ticketing.repository;

import com.prarambh.act.one.ticketing.model.Auditorium;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriumRepository extends JpaRepository<Auditorium, String> {
    /** Find the first auditorium that matches the given showId (useful when there is a 1:1 mapping).
     *  Returns an Optional.empty() when none found.
     */
    Optional<Auditorium> findFirstByShowId(String showId);
}
