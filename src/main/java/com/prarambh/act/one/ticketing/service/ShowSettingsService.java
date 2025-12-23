package com.prarambh.act.one.ticketing.service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
public class ShowSettingsService {

    private final AtomicReference<String> defaultShowName = new AtomicReference<>();

    /**
     * @return the default show name if present and non-blank
     */
    public Optional<String> getDefaultShowName() {
        return Optional.ofNullable(defaultShowName.get()).map(String::trim).filter(s -> !s.isBlank());
    }

    /**
     * Sets the default show name.
     */
    public void setDefaultShowName(String showName) {
        defaultShowName.set(showName == null ? null : showName.trim());
    }

    /**
     * Clears the default show name.
     */
    public void clearDefaultShowName() {
        defaultShowName.set(null);
    }
}
