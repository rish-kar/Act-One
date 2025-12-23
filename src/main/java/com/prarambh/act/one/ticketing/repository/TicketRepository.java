package com.prarambh.act.one.ticketing.repository;

import com.prarambh.act.one.ticketing.model.Ticket;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@link Ticket} entities.
 */
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Bulk update to set {@code showName} for all records.
     *
     * <p>Used when an admin changes the default show name and wants the change reflected across
     * existing tickets.
     *
     * @param showName new show name
     * @return number of updated rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Ticket t set t.showName = :showName")
    int updateShowNameForAll(String showName);
}
