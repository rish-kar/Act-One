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
 */
@Entity
@Table(name = "tickets")
@Slf4j
public class Ticket {

    @Id
    private UUID ticketId;

    @Column(nullable = true)
    private String showId;

    @Column(nullable = false)
    private String showName;

    @Column(nullable = true, name = "auditorium_id")
    private String auditoriumId;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = true)
    private String email;

    @Column(nullable = false)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(nullable = false)
    private LocalDate createdAtDate;

    @Convert(converter = Ist12HourTimeConverter.class)
    @Column(nullable = false)
    private LocalTime createdAtTime;

    private LocalDate usedAtDate;

    @Convert(converter = Ist12HourTimeConverter.class)
    @Column
    private LocalTime usedAtTime;

    @Column(nullable = false, length = 18)
    private String qrCodeId;

    @Column(nullable = false)
    private int ticketCount = 1;

    @Column(name = "user_id", nullable = true, length = 32)
    private String userId;

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

    public String getAuditoriumId() {
        return auditoriumId;
    }

    public void setAuditoriumId(String auditoriumId) {
        this.auditoriumId = auditoriumId;
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

    public String getQrCodeId() {
        return qrCodeId;
    }

    public void setQrCodeId(String qrCodeId) {
        if (qrCodeId == null) {
            this.qrCodeId = null;
            return;
        }
        this.qrCodeId = normalizeBarcodeId18(qrCodeId);
    }

    private static String normalizeBarcodeId18(String input) {
        String s = input.trim().replace("-", "").replace(" ", "");
        if (s.length() == 18) {
            return s;
        }
        if (s.length() > 18) {
            return s.substring(0, 18);
        }
        String extra = UUID.randomUUID().toString().replace("-", "");
        return (s + extra).substring(0, 18);
    }

    public int getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(int ticketCount) {
        this.ticketCount = ticketCount;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public void markUsed() {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowIst = ZonedDateTime.now(ist);
        this.status = TicketStatus.USED;
        this.usedAtDate = nowIst.toLocalDate();
        this.usedAtTime = nowIst.toLocalTime();
        log.info("Ticket marked USED: ticketId={}, usedAtDate={}, usedAtTime={} (stored via converter)", ticketId, usedAtDate, usedAtTime);
    }

    @PrePersist
    protected void onCreate() {
        if (ticketId == null) {
            ticketId = UUID.randomUUID();
        }
        if (qrCodeId == null || qrCodeId.isBlank()) {
            qrCodeId = UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        } else {
            qrCodeId = normalizeBarcodeId18(qrCodeId);
        }

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
            status = TicketStatus.ISSUED;
        }

        if (ticketCount <= 0) {
            ticketCount = 1;
        }

        log.info("Ticket pre-persist initialized: ticketId={}, qrCodeId={}, showId={}, showName='{}', status={}, ticketCount={}, createdAtDate={}, createdAtTime={} ",
                ticketId, qrCodeId, showId, showName, status, ticketCount, createdAtDate, createdAtTime);
    }
}
