package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.repository.TicketRepository;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates a 4-digit customerId (1000-9999).
 *
 * <p>This is best-effort uniqueness across all tickets.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerIdService {

    private static final int MIN = 1000;
    private static final int MAX = 9999;

    private final TicketRepository ticketRepository;
    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a customerId that is not currently present in the DB.
     *
     * <p>Note: Without a dedicated Customer table + DB unique constraint, perfect non-collision
     * under extreme concurrency can't be guaranteed. For this app's usage (manual validation flow),
     * this is sufficient.
     */
    @Transactional(readOnly = true)
    public int allocateUniqueCustomerId() {
        // Try random a few times.
        for (int attempt = 1; attempt <= 25; attempt++) {
            int candidate = MIN + random.nextInt((MAX - MIN) + 1);
            if (!ticketRepository.existsByCustomerId(candidate)) {
                log.debug("event=customer_id_allocated method=random attempt={} customerId={}", attempt, candidate);
                return candidate;
            }
        }

        // Fallback: scan deterministically.
        for (int candidate = MIN; candidate <= MAX; candidate++) {
            if (!ticketRepository.existsByCustomerId(candidate)) {
                log.debug("event=customer_id_allocated method=scan customerId={}", candidate);
                return candidate;
            }
        }

        throw new IllegalStateException("No customer IDs available (1000-9999 exhausted)");
    }
}

