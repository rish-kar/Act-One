package com.prarambh.act.one.ticketing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Auditorium entity representing seating/state for a show.
 */
@Entity
@Table(name = "auditoriums")
public class Auditorium {

    @Id
    @Column(name = "auditorium_id", length = 64)
    private String auditoriumId;

    @Column(nullable = false)
    private String auditoriumName;

    @Column(nullable = false)
    private String showId;

    @Column(nullable = false)
    private String showName;

    @Column(nullable = false)
    private LocalDate showDate;

    @Column(nullable = false)
    private LocalTime showTime;

    @Column(nullable = false)
    private int totalSeats;

    @Column(nullable = false)
    private int reservedSeats;

    @Column(nullable = false)
    private int bookedSeats;

    @Column(nullable = false)
    private int confirmedSeats;

    @Column(nullable = false)
    private int checkedInSeats;

    @Column(nullable = false)
    private int availableSeats;

    @Column(nullable = false, name = "ticket_amount", precision = 10, scale = 2)
    private BigDecimal ticketAmount;

    // getters and setters

    public String getAuditoriumId() {
        return auditoriumId;
    }

    public void setAuditoriumId(String auditoriumId) {
        this.auditoriumId = auditoriumId;
    }

    public String getAuditoriumName() {
        return auditoriumName;
    }

    public void setAuditoriumName(String auditoriumName) {
        this.auditoriumName = auditoriumName;
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

    public LocalDate getShowDate() {
        return showDate;
    }

    public void setShowDate(LocalDate showDate) {
        this.showDate = showDate;
    }

    public LocalTime getShowTime() {
        return showTime;
    }

    public void setShowTime(LocalTime showTime) {
        this.showTime = showTime;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public int getReservedSeats() {
        return reservedSeats;
    }

    public void setReservedSeats(int reservedSeats) {
        this.reservedSeats = reservedSeats;
    }

    public int getBookedSeats() {
        return bookedSeats;
    }

    public void setBookedSeats(int bookedSeats) {
        this.bookedSeats = bookedSeats;
    }

    public int getConfirmedSeats() {
        return confirmedSeats;
    }

    public void setConfirmedSeats(int confirmedSeats) {
        this.confirmedSeats = confirmedSeats;
    }

    public int getCheckedInSeats() {
        return checkedInSeats;
    }

    public void setCheckedInSeats(int checkedInSeats) {
        this.checkedInSeats = checkedInSeats;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(int availableSeats) {
        this.availableSeats = availableSeats;
    }

    public BigDecimal getTicketAmount() {
        return ticketAmount;
    }

    public void setTicketAmount(BigDecimal ticketAmount) {
        this.ticketAmount = ticketAmount;
    }
}
