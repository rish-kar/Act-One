package com.prarambh.act.one.ticketing.model;

import com.prarambh.act.one.ticketing.service.ShowIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Ticket entity stored in the database.
 *
 * <p>Important notes:
 * <ul>
 *   <li>All timestamps are captured in IST ({@code Asia/Kolkata}).</li>
 *   <li>Dates and times are stored as separate columns.</li>
 *   <li>{@code barcodeId} is a short identifier intended to be encoded as a barcode/QR code.</li>
 * </ul>
 */
@Entity
@Table(name = "tickets")
@Slf4j
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

    /** Short barcode identifier intended for barcode representation. Stored as exactly 18 characters. */
    @Column(nullable = false, length = 18)
    private String barcodeId;

    /**
     * Number of tickets purchased in the same purchase request.
     *
     * <p>When a customer buys N tickets, the system creates N ticket rows and each row stores
     * the same {@code ticketCount=N} for traceability.
     */
    @Column(nullable = false)
    private int ticketCount = 1;

    /**
     * 4-digit customer identifier (1000-9999). Not unique across rows.
     *
     * <p>Same customer can have multiple rows (multiple tickets / multiple purchases), so this value
     * can repeat across tickets.
     */
    @Column(nullable = true)
    private Integer customerId;

    /**
     * Transaction identifier provided by the buyer (e.g., UPI transaction ID).
     *
     * <p>Used for manual validation before tickets are issued.
     */
    @Column(nullable = true)
    private String transactionId;

    @Column(name = "ticket_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal ticketAmount;

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

    public String getBarcodeId() {
        return barcodeId;
    }

    public void setBarcodeId(String barcodeId) {
        if (barcodeId == null) {
            this.barcodeId = null;
            return;
        }
        this.barcodeId = normalizeBarcodeId18(barcodeId);
    }

    private static String normalizeBarcodeId18(String input) {
        String s = input.trim().replace("-", "").replace(" ", "");
        if (s.length() == 18) {
            return s;
        }
        if (s.length() > 18) {
            return s.substring(0, 18);
        }
        // If shorter than 18, append UUID hex to reach 18.
        String extra = UUID.randomUUID().toString().replace("-", "");
        return (s + extra).substring(0, 18);
    }

    public int getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(int ticketCount) {
        this.ticketCount = ticketCount;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public BigDecimal getTicketAmount() {
        return ticketAmount;
    }

    public void setTicketAmount(BigDecimal ticketAmount) {
        this.ticketAmount = ticketAmount;
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
        log.info("Ticket marked USED: ticketId={}, usedAtDate={}, usedAtTime={} (stored via converter)", ticketId, usedAtDate, usedAtTime);
    }

    /**
     * Entity lifecycle callback to ensure IDs and created-at fields are set.
     */
    @PrePersist
    protected void onCreate() {
        if (ticketId == null) {
            ticketId = UUID.randomUUID();
        }
        if (barcodeId == null || barcodeId.isBlank()) {
            // Generate and store barcodeId as exactly 18 characters (hyphen-free).
            barcodeId = UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        } else {
            // Ensure any externally provided barcodeId is normalized.
            barcodeId = normalizeBarcodeId18(barcodeId);
        }

        // Keep showId short and related to showName.
        if ((showId == null || showId.isBlank()) && showName != null && !showName.isBlank()) {
            showId = ShowIdGenerator.fromShowName(showName);
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
            // Default is ISSUED for legacy issuance flow, but transaction-first endpoints will
            // explicitly set TRANSACTION_MADE.
            status = TicketStatus.ISSUED;
        }

        if (ticketCount <= 0) {
            ticketCount = 1;
        }

        log.info("Ticket pre-persist initialized: ticketId={}, barcodeId={}, showId={}, showName='{}', status={}, ticketCount={}, createdAtDate={}, createdAtTime={} ",
                ticketId, barcodeId, showId, showName, status, ticketCount, createdAtDate, createdAtTime);
    }
}
