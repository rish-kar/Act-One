package com.prarambh.act.one.ticketing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Ticket entity stored in the database.
 *
 * <p>Important notes:
 * <ul>
 *   <li>All timestamps are captured in IST ({@code Asia/Kolkata}).</li>
 *   <li>Dates and times are stored as separate columns.</li>
 *   <li>{@code barcodeId} is a separate UUID intended to be encoded as a barcode/QR code.</li>
 * </ul>
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    /** Primary key identifier for the ticket. */
    @Id
    private UUID ticketId;

    /** Show ID is currently nullable (future expansion). */
    @Column(nullable = true)
    private String showId;

    /** Human-readable show name (can be updated by admin endpoints). */
    @Column(nullable = false)
    private String showName;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = true)
    private String email;

    @Column(nullable = false)
    private String phoneNumber;

    /** Ticket lifecycle status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    /** Creation date in IST. */
    @Column(nullable = false)
    private LocalDate createdAtDate;

    /**
     * Creation time in IST.
     *
     * Stored as a 12-hour string using {@link Ist12HourTimeConverter}.
     */
    @Convert(converter = Ist12HourTimeConverter.class)
    @Column(nullable = false)
    private LocalTime createdAtTime;

    /** Check-in date in IST (nullable until checked-in). */
    private LocalDate usedAtDate;

    /**
     * Check-in time in IST (nullable until checked-in).
     * Stored as a 12-hour string using {@link Ist12HourTimeConverter}.
     */
    @Convert(converter = Ist12HourTimeConverter.class)
    @Column
    private LocalTime usedAtTime;

    /** UUID intended for barcode/QR representation and scanning at the venue. */
    @Column(nullable = false)
    private UUID barcodeId;

    public UUID getTicketId() {
        return ticketId;
    }

    public void setTicketId(UUID ticketId) {
        this.ticketId = ticketId;
    }

    public String getShowId() {
        return showId;
    }

    public void setShowId(String showId) {
        this.showId = showId;
    }

    public String getShowName() {
        return showName;
    }

    public void setShowName(String showName) {
        this.showName = showName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public LocalDate getCreatedAtDate() {
        return createdAtDate;
    }

    public void setCreatedAtDate(LocalDate createdAtDate) {
        this.createdAtDate = createdAtDate;
    }

    public LocalTime getCreatedAtTime() {
        return createdAtTime;
    }

    public void setCreatedAtTime(LocalTime createdAtTime) {
        this.createdAtTime = createdAtTime;
    }

    public LocalDate getUsedAtDate() {
        return usedAtDate;
    }

    public void setUsedAtDate(LocalDate usedAtDate) {
        this.usedAtDate = usedAtDate;
    }

    public LocalTime getUsedAtTime() {
        return usedAtTime;
    }

    public void setUsedAtTime(LocalTime usedAtTime) {
        this.usedAtTime = usedAtTime;
    }

    public UUID getBarcodeId() {
        return barcodeId;
    }

    public void setBarcodeId(UUID barcodeId) {
        this.barcodeId = barcodeId;
    }

    /**
     * Mark the ticket as used (checked in) using IST clock.
     */
    public void markUsed() {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowIst = ZonedDateTime.now(ist);
        this.status = TicketStatus.USED;
        this.usedAtDate = nowIst.toLocalDate();
        this.usedAtTime = nowIst.toLocalTime();
    }

    /**
     * Entity lifecycle callback to ensure IDs and created-at fields are set.
     */
    @PrePersist
    protected void onCreate() {
        if (ticketId == null) {
            ticketId = UUID.randomUUID();
        }
        if (barcodeId == null) {
            barcodeId = UUID.randomUUID();
        }
        if (showId == null || showId.isBlank()) {
            showId = "SHOW-" + UUID.randomUUID();
        }

        ZoneId ist = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowIst = ZonedDateTime.now(ist);
        if (createdAtDate == null) {
            createdAtDate = nowIst.toLocalDate();
        }
        if (createdAtTime == null) {
            createdAtTime = nowIst.toLocalTime();
        }

        if (status == null) {
            status = TicketStatus.ISSUED;
        }
    }
}
