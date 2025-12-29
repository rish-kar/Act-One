package com.prarambh.act.one.ticketing.service;

import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates a short userId (8-char UUID).
 *
 * <p>This is best-effort uniqueness across all tickets.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserIdService {

    private final com.prarambh.act.one.ticketing.repository.TicketRepository ticketRepository;
    private final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public String allocateUniqueUserId() {
        for (int attempt = 1; attempt <= 25; attempt++) {
            String candidate = generateShortUuid();
            if (!ticketRepository.existsByUserId(candidate)) {
                log.debug("event=user_id_allocated method=random attempt={} userId={}", attempt, candidate);
                return candidate;
            }
        }

        for (int i = 0; i < 1000; i++) {
            String candidate = generateShortUuid();
            if (!ticketRepository.existsByUserId(candidate)) {
                log.debug("event=user_id_allocated method=scan userId={}", candidate);
                return candidate;
            }
        }

        throw new IllegalStateException("No user IDs available (collision exhaustion)");
    }

    private String generateShortUuid() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}

