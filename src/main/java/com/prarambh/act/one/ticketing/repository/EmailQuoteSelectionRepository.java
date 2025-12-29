package com.prarambh.act.one.ticketing.repository;

import com.prarambh.act.one.ticketing.model.EmailQuoteSelection;
import com.prarambh.act.one.ticketing.model.EmailQuoteType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailQuoteSelectionRepository extends JpaRepository<EmailQuoteSelection, Long> {
    Optional<EmailQuoteSelection> findByUserIdAndEmailType(String userId, EmailQuoteType emailType);
}
