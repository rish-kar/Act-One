package com.prarambh.act.one.ticketing.service.quotes;

import com.prarambh.act.one.ticketing.model.EmailQuoteSelection;
import com.prarambh.act.one.ticketing.model.EmailQuoteType;
import com.prarambh.act.one.ticketing.repository.EmailQuoteSelectionRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picks a random theatre quote for an email and persists it per userId.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteSelectionService {

    private final TheatreQuotesCatalog catalog;
    private final EmailQuoteSelectionRepository selectionRepository;

    private final SecureRandom random = new SecureRandom();

    @Transactional
    public TheatreQuote quoteForUser(String userId, EmailQuoteType type) {
        List<TheatreQuote> quotes = catalog.getQuotes();
        if (quotes == null || quotes.isEmpty()) {
            return new TheatreQuote("quotes.none", "", "");
        }

        // If no userId, best effort random (can't guarantee cross-email uniqueness).
        if (userId == null) {
            return quotes.get(random.nextInt(quotes.size()));
        }

        Optional<EmailQuoteSelection> existing = selectionRepository.findByUserIdAndEmailType(userId, type);
        if (existing.isPresent()) {
            return findByKeyOrFallback(existing.get().getQuoteKey(), quotes);
        }

        // To enforce "issue != check-in" pick, avoid the other type's already selected quote (if any).
        EmailQuoteType otherType = (type == EmailQuoteType.ISSUE) ? EmailQuoteType.CHECK_IN : EmailQuoteType.ISSUE;
        String avoidKey = selectionRepository.findByUserIdAndEmailType(userId, otherType)
                .map(EmailQuoteSelection::getQuoteKey)
                .orElse(null);

        TheatreQuote chosen = pickRandomAvoiding(quotes, avoidKey);

        EmailQuoteSelection sel = new EmailQuoteSelection();
        sel.setUserId(userId);
        sel.setEmailType(type);
        sel.setQuoteKey(chosen.key());
        selectionRepository.save(sel);

        log.debug("event=email_quote_selected userId={} emailType={} quoteKey={} avoidedKey={}", userId, type, chosen.key(), avoidKey);
        return chosen;
    }

    private TheatreQuote pickRandomAvoiding(List<TheatreQuote> quotes, String avoidKey) {
        if (avoidKey == null || avoidKey.isBlank() || quotes.size() == 1) {
            return quotes.get(random.nextInt(quotes.size()));
        }

        // Try a few times, then fall back to first different.
        for (int i = 0; i < 10; i++) {
            TheatreQuote q = quotes.get(random.nextInt(quotes.size()));
            if (!avoidKey.equals(q.key())) {
                return q;
            }
        }

        for (TheatreQuote q : quotes) {
            if (!avoidKey.equals(q.key())) {
                return q;
            }
        }

        return quotes.get(0);
    }

    private TheatreQuote findByKeyOrFallback(String key, List<TheatreQuote> quotes) {
        if (key != null) {
            for (TheatreQuote q : quotes) {
                if (key.equals(q.key())) {
                    return q;
                }
            }
        }
        return quotes.get(0);
    }
}

