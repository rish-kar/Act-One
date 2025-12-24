package com.prarambh.act.one.ticketing.repository;

import com.prarambh.act.one.ticketing.model.Ticket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@link Ticket} entities.
 */
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Lookup a ticket by its barcode id.
     */
    Optional<Ticket> findByBarcodeId(String barcodeId);

    /**
     * Bulk update to set {@code showName} and {@code showId} for all records.
     *
     * <p>Used when an admin changes the default show name and wants the change reflected across
     * existing tickets.
     *
     * @param showName new show name
     * @param showId new show id
     * @return number of updated rows
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Ticket t set t.showName = :showName, t.showId = :showId")
    int updateShowNameAndShowIdForAll(String showName, String showId);

    /** Count remaining ISSUED tickets for a best-effort purchase grouping. */
    long countByEmailIgnoreCaseAndPhoneNumberIgnoreCaseAndShowIdAndStatus(String email, String phoneNumber, String showId, com.prarambh.act.one.ticketing.model.TicketStatus status);

    /** Fetch all tickets for a best-effort purchase grouping. */
    List<Ticket> findByEmailIgnoreCaseAndPhoneNumberIgnoreCaseAndShowId(String email, String phoneNumber, String showId);

    /** Find all tickets where phoneNumber ends with the given 10 digits. */
    List<Ticket> findByPhoneNumberEndingWith(String last10Digits);

    /** Find tickets whose ticketId UUID string ends with the given suffix (case-insensitive). */
    @Query("select t from Ticket t where lower(cast(t.ticketId as string)) like concat('%', lower(:suffix))")
    List<Ticket> findByTicketIdSuffix(String suffix);
}
