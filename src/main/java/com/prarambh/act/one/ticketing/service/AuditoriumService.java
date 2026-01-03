package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Auditorium;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.repository.AuditoriumRepository;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuditoriumService {

    private final AuditoriumRepository auditoriumRepository;
    private final TicketRepository ticketRepository;

    /** Deterministic ID derived from auditoriumName (stable across restarts). */
    public static String auditoriumIdFromName(String auditoriumName) {
        String base = auditoriumName == null ? "" : auditoriumName.trim().toUpperCase();
        if (base.isBlank()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(base.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash);
            return "AUD-" + hex.substring(0, 12).toUpperCase();
        } catch (Exception e) {
            return "AUD-" + Math.abs(base.hashCode());
        }
    }

    private static int clampNonNegative(int v) { return Math.max(0, v); }

    public static int computeAvailable(int total, int reserved, int booked, int confirmed) {
        int used = clampNonNegative(reserved) + clampNonNegative(booked) + clampNonNegative(confirmed);
        return Math.max(0, clampNonNegative(total) - used);
    }

    @Transactional
    public Auditorium upsert(String showId, String showName, String auditoriumName, LocalDate showDate, LocalTime showTime, java.math.BigDecimal ticketAmount, int totalSeats, int reservedSeats) {
        if (!StringUtils.hasText(showId)) throw new IllegalArgumentException("showId required");
        if (!StringUtils.hasText(showName)) throw new IllegalArgumentException("showName required");
        if (!StringUtils.hasText(auditoriumName)) throw new IllegalArgumentException("auditoriumName required");
        if (showDate == null) throw new IllegalArgumentException("showDate required");
        if (showTime == null) throw new IllegalArgumentException("showTime required");
        if (ticketAmount == null) throw new IllegalArgumentException("ticketAmount required");
        if (totalSeats <= 0) throw new IllegalArgumentException("totalSeats must be > 0");
        if (reservedSeats < 0) throw new IllegalArgumentException("reservedSeats must be >= 0");

        String id = auditoriumIdFromName(auditoriumName);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("unable to generate auditoriumId from auditoriumName");
        }
        Auditorium a = auditoriumRepository.findById(id).orElseGet(Auditorium::new);
        a.setAuditoriumId(id);
        a.setAuditoriumName(auditoriumName.trim());
        a.setShowId(showId.trim());
        a.setShowName(showName.trim());
        a.setShowDate(showDate);
        a.setShowTime(showTime);
        a.setTicketAmount(ticketAmount);
        a.setTotalSeats(totalSeats);
        a.setReservedSeats(reservedSeats);

        // Recalculate seats from tickets
        recalcInto(a);
        return auditoriumRepository.save(a);
    }

    @Transactional(readOnly = true)
    public java.util.List<Auditorium> findAll() {
        return auditoriumRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Auditorium> findById(String auditoriumId) {
        if (!StringUtils.hasText(auditoriumId)) return Optional.empty();
        return auditoriumRepository.findById(auditoriumId.trim());
    }

    @Transactional
    public Auditorium recalc(String auditoriumId) {
        Auditorium a = auditoriumRepository.findById(auditoriumId).orElseThrow(() -> new IllegalArgumentException("auditorium not found"));
        recalcInto(a);
        return auditoriumRepository.save(a);
    }

    /** Best-effort: recalc the auditorium that matches the given showId (if any). */
    @Transactional
    public void recalcByShowIdIfPresent(String showId) {
        if (!org.springframework.util.StringUtils.hasText(showId)) return;
        String s = showId.trim();
        // avoid relying on a derived repository query to keep compilation robust
        auditoriumRepository.findAll().stream()
                .filter(a -> s.equals(a.getShowId()))
                .findFirst()
                .ifPresent(a -> {
                    recalcInto(a);
                    auditoriumRepository.save(a);
                });
    }

    private void recalcInto(Auditorium a) {
        String showId = a.getShowId();

        // Compute by loading all tickets and filtering by showId.
        var all = ticketRepository.findAll();
        int bookedSeats = 0;
        int confirmedSeats = 0;
        int checkedInSeats = 0;
        for (var t : all) {
            if (t.getShowId() == null || !t.getShowId().equals(showId)) continue;
            if (t.getStatus() == TicketStatus.TRANSACTION_MADE || t.getStatus() == TicketStatus.TRANSACTION_PENDING) bookedSeats++;
            if (t.getStatus() == TicketStatus.ISSUED) confirmedSeats++;
            if (t.getStatus() == TicketStatus.USED) checkedInSeats++;
        }
        a.setBookedSeats(bookedSeats);
        a.setConfirmedSeats(confirmedSeats);
        a.setCheckedInSeats(checkedInSeats);
        a.setAvailableSeats(computeAvailable(a.getTotalSeats(), a.getReservedSeats(), bookedSeats, confirmedSeats));
    }

    @Transactional
    public Auditorium update(String auditoriumId, String showId, String showName, String auditoriumName, LocalDate showDate, LocalTime showTime, java.math.BigDecimal ticketAmount, int totalSeats, int reservedSeats) {
        if (!StringUtils.hasText(auditoriumId)) throw new IllegalArgumentException("auditoriumId required");
        if (!StringUtils.hasText(showId)) throw new IllegalArgumentException("showId required");
        if (!StringUtils.hasText(showName)) throw new IllegalArgumentException("showName required");
        if (!StringUtils.hasText(auditoriumName)) throw new IllegalArgumentException("auditoriumName required");
        if (showDate == null) throw new IllegalArgumentException("showDate required");
        if (showTime == null) throw new IllegalArgumentException("showTime required");
        if (ticketAmount == null) throw new IllegalArgumentException("ticketAmount required");
        if (totalSeats <= 0) throw new IllegalArgumentException("totalSeats must be > 0");
        if (reservedSeats < 0) throw new IllegalArgumentException("reservedSeats must be >= 0");

        Auditorium a = auditoriumRepository.findById(auditoriumId.trim()).orElseThrow(() -> new IllegalArgumentException("auditorium not found"));
        a.setShowId(showId.trim());
        a.setShowName(showName.trim());
        a.setAuditoriumName(auditoriumName.trim());
        a.setShowDate(showDate);
        a.setShowTime(showTime);
        a.setTicketAmount(ticketAmount);
        a.setTotalSeats(totalSeats);
        a.setReservedSeats(reservedSeats);
        recalcInto(a);
        return auditoriumRepository.save(a);
    }

    @Transactional
    public Auditorium patch(String auditoriumId, String showId, String showName, String auditoriumName, LocalDate showDate, LocalTime showTime, java.math.BigDecimal ticketAmount, Integer totalSeats, Integer reservedSeats) {
        if (!StringUtils.hasText(auditoriumId)) throw new IllegalArgumentException("auditoriumId required");
        Auditorium a = auditoriumRepository.findById(auditoriumId.trim()).orElseThrow(() -> new IllegalArgumentException("auditorium not found"));

        if (StringUtils.hasText(showId)) a.setShowId(showId.trim());
        if (StringUtils.hasText(showName)) a.setShowName(showName.trim());
        if (StringUtils.hasText(auditoriumName)) a.setAuditoriumName(auditoriumName.trim());
        if (showDate != null) a.setShowDate(showDate);
        if (showTime != null) a.setShowTime(showTime);
        if (ticketAmount != null) a.setTicketAmount(ticketAmount);
        if (totalSeats != null) {
            if (totalSeats <= 0) throw new IllegalArgumentException("totalSeats must be > 0");
            a.setTotalSeats(totalSeats);
        }
        if (reservedSeats != null) {
            if (reservedSeats < 0) throw new IllegalArgumentException("reservedSeats must be >= 0");
            a.setReservedSeats(reservedSeats);
        }

        recalcInto(a);
        return auditoriumRepository.save(a);
    }

    @Transactional
    public void delete(String auditoriumId) {
        if (!StringUtils.hasText(auditoriumId)) throw new IllegalArgumentException("auditoriumId required");
        auditoriumRepository.deleteById(auditoriumId.trim());
    }
}
