package com.prarambh.act.one.ticketing.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Stores which quote was selected for a given userId and email type.
 */
@Entity
@Table(
        name = "email_quote_selections",
        uniqueConstraints = @UniqueConstraint(name = "uk_quote_user_type", columnNames = {"user_id", "email_type"})
)
@Getter
@Setter
public class EmailQuoteSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 32)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 16)
    private EmailQuoteType emailType;

    /** Key from theatre-quotes.properties (e.g., quotes.12). */
    @Column(name = "quote_key", nullable = false, length = 64)
    private String quoteKey;

    @Column(name = "created_at_date", nullable = false)
    private LocalDate createdAtDate;

    @Column(name = "created_at_time", nullable = false)
    private LocalTime createdAtTime;

    @PrePersist
    void onCreate() {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowIst = ZonedDateTime.now(ist);
        if (createdAtDate == null) {
            createdAtDate = nowIst.toLocalDate();
        }
        if (createdAtTime == null) {
            createdAtTime = nowIst.toLocalTime();
        }
    }
}
