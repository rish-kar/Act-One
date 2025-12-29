package com.prarambh.act.one.ticketing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;

/** Stores auditorium/show seating state for a specific show. */
@Entity
@Table(name = "auditoriums")
public class Auditorium {

    @Id
    @Column(name = "auditorium_id", length = 64)
    private String auditoriumId;

    @Column(name = "auditorium_name", nullable = false)
    private String auditoriumName;

    @Column(name = "show_id", nullable = false)
    private String showId;

    @Column(name = "show_date", nullable = false)
    private LocalDate showDate;

    @Column(name = "show_time", nullable = false)
    private LocalTime showTime;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "reserved_seats", nullable = false)
    private int reservedSeats;

    @Column(name = "booked_seats", nullable = false)
    private int bookedSeats;

    @Column(name = "confirmed_seats", nullable = false)
    private int confirmedSeats;

    @Column(name = "checked_in_seats", nullable = false)
    private int checkedInSeats;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    public String getAuditoriumId() { return auditoriumId; }
    public void setAuditoriumId(String auditoriumId) { this.auditoriumId = auditoriumId; }

    public String getAuditoriumName() { return auditoriumName; }
    public void setAuditoriumName(String auditoriumName) { this.auditoriumName = auditoriumName; }

    public String getShowId() { return showId; }
    public void setShowId(String showId) { this.showId = showId; }

    public LocalDate getShowDate() { return showDate; }
    public void setShowDate(LocalDate showDate) { this.showDate = showDate; }

    public LocalTime getShowTime() { return showTime; }
    public void setShowTime(LocalTime showTime) { this.showTime = showTime; }

    public int getTotalSeats() { return totalSeats; }
    public void setTotalSeats(int totalSeats) { this.totalSeats = totalSeats; }

    public int getReservedSeats() { return reservedSeats; }
    public void setReservedSeats(int reservedSeats) { this.reservedSeats = reservedSeats; }

    public int getBookedSeats() { return bookedSeats; }
    public void setBookedSeats(int bookedSeats) { this.bookedSeats = bookedSeats; }

    public int getConfirmedSeats() { return confirmedSeats; }
    public void setConfirmedSeats(int confirmedSeats) { this.confirmedSeats = confirmedSeats; }

    public int getCheckedInSeats() { return checkedInSeats; }
    public void setCheckedInSeats(int checkedInSeats) { this.checkedInSeats = checkedInSeats; }

    public int getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(int availableSeats) { this.availableSeats = availableSeats; }
}

