package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for check-in so we can trigger side effects (emails) consistently.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketCheckInService {

    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Optional<Ticket> checkInByBarcode(String barcodeId) {
        Optional<Ticket> ticketOpt = ticketRepository.findByBarcodeId(barcodeId);
        if (ticketOpt.isEmpty()) {
            return Optional.empty();
        }

        Ticket ticket = ticketOpt.get();
        if (ticket.getStatus() == TicketStatus.USED) {
            return Optional.of(ticket);
        }

        ticket.markUsed();
        Ticket saved = ticketRepository.save(ticket);

        // Only email when ALL tickets from the same purchase group are now USED.
        if (saved.getEmail() != null && saved.getPhoneNumber() != null && saved.getShowId() != null) {
            long remainingIssued = ticketRepository.countByEmailIgnoreCaseAndPhoneNumberIgnoreCaseAndShowIdAndStatus(
                    saved.getEmail(),
                    saved.getPhoneNumber(),
                    saved.getShowId(),
                    TicketStatus.ISSUED
            );

            if (remainingIssued == 0) {
                List<Ticket> groupTickets = ticketRepository.findByEmailIgnoreCaseAndPhoneNumberIgnoreCaseAndShowId(
                        saved.getEmail(),
                        saved.getPhoneNumber(),
                        saved.getShowId()
                );
                eventPublisher.publishEvent(new TicketPurchaseCheckedInEvent(List.copyOf(groupTickets)));
                log.debug("event=purchase_checked_in_all_used groupEmail={} showId={} ticketCount={}", saved.getEmail(), saved.getShowId(), groupTickets.size());
            } else {
                log.debug("event=purchase_checked_in_partial remainingIssued={} groupEmail={} showId={}", remainingIssued, saved.getEmail(), saved.getShowId());
            }
        }

        log.debug("event=ticket_checked_in_persisted ticketId={} barcodeId={}", saved.getTicketId(), barcodeId);
        return Optional.of(saved);
    }
}
