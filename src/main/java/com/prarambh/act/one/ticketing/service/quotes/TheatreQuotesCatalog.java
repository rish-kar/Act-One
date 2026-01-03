package com.prarambh.act.one.ticketing.service.quotes;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Loads theatre quotes from classpath:theatre-quotes.properties.
 *
 * <p>Quotes are expected to be defined as properties with keys starting with {@code quotes.}.
 */
@Component
@Slf4j
public class TheatreQuotesCatalog {

    @Getter
    private final List<TheatreQuote> quotes;

    /**
     * Construct and load quotes immediately.
     */
    public TheatreQuotesCatalog() {
        this.quotes = Collections.unmodifiableList(load());
        log.info("event=theatre_quotes_loaded count={}", this.quotes.size());
    }

    private List<TheatreQuote> load() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("theatre-quotes.properties")) {
            if (in == null) {
                log.warn("event=theatre_quotes_missing resource=theatre-quotes.properties");
                return List.of();
            }

            Properties props = new Properties();
            props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));

            List<TheatreQuote> list = new ArrayList<>();
            for (String name : props.stringPropertyNames()) {
                if (!name.startsWith("quotes.")) {
                    continue;
                }
                String value = props.getProperty(name);
                if (value == null || value.isBlank()) {
                    continue;
                }
                list.add(TheatreQuote.fromProperty(name, value));
            }

            // stable order for deterministic tests if needed
            list.sort(java.util.Comparator.comparing(TheatreQuote::key));
            return list;
        } catch (Exception e) {
            log.error("event=theatre_quotes_load_failed error={}", e.toString());
            return List.of();
        }
    }
}
