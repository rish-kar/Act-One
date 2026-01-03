package com.prarambh.act.one.ticketing.service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * In-memory runtime settings.
 *
 * <p>Currently stores the "default show name" that will be applied to newly issued tickets when
 * the request does not provide {@code showName}.
 *
 * <p>Thread-safety: implemented using an {@link AtomicReference}.
 *
 * <p>Persistence: this value is NOT persisted to the database; it resets on application restart.
 */
@Service
@Slf4j
public class ShowSettingsService {

    private final AtomicReference<String> defaultShowName = new AtomicReference<>();
    private final AtomicReference<java.time.LocalDate> defaultShowDate = new AtomicReference<>();
    private final AtomicReference<java.time.LocalTime> defaultShowTime = new AtomicReference<>();

    /**
     * @return the default show name if present and non-blank
     */
    public Optional<String> getDefaultShowName() {
        return Optional.ofNullable(defaultShowName.get()).map(String::trim).filter(s -> !s.isBlank());
    }

    /**
     * Sets the default show name.
     *
     * @param showName show name to set
     */
    public void setDefaultShowName(String showName) {
        String trimmed = showName == null ? null : showName.trim();
        defaultShowName.set(trimmed);
        log.info("Default showName updated in memory: '{}'", trimmed);
    }

    /**
     * Clears the default show name.
     */
    public void clearDefaultShowName() {
        defaultShowName.set(null);
        log.info("Default showName cleared in memory");
    }

    /**
     * @return the default show date if present
     */
    public Optional<java.time.LocalDate> getDefaultShowDate() {
        return Optional.ofNullable(defaultShowDate.get());
    }

    /**
     * Sets the default show date.
     *
     * @param showDate show date to set
     */
    public void setDefaultShowDate(java.time.LocalDate showDate) {
        defaultShowDate.set(showDate);
        log.info("Default showDate updated in memory: '{}'", showDate);
    }

    /**
     * @return the default show time if present
     */
    public Optional<java.time.LocalTime> getDefaultShowTime() {
        return Optional.ofNullable(defaultShowTime.get());
    }

    /**
     * Sets the default show time.
     *
     * @param showTime show time to set
     */
    public void setDefaultShowTime(java.time.LocalTime showTime) {
        defaultShowTime.set(showTime);
        log.info("Default showTime updated in memory: '{}'", showTime);
    }
}
