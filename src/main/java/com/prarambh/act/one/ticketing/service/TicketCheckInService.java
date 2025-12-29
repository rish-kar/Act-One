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
    private final AuditoriumService auditoriumService;

    @Transactional
    public Optional<Ticket> checkInByBarcode(String qrCodeId) {
        Optional<Ticket> ticketOpt = ticketRepository.findByQrCodeId(qrCodeId);
        if (ticketOpt.isEmpty()) {
            return Optional.empty();
        }

        Ticket ticket = ticketOpt.get();
        if (ticket.getStatus() == TicketStatus.USED) {
            return Optional.of(ticket);
        }

        ticket.markUsed();
        Ticket saved = ticketRepository.save(ticket);

        // Update auditorium seat counts (checked-in seats).
        auditoriumService.recalcByShowIdIfPresent(saved.getShowId());

        // Only email when ALL tickets from the same purchase group are now USED.
        // Primary grouping: (email, phone, showId) for legacy flows.
        // Fallback grouping: userId (manual transaction flow can have null showId).
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
        } else if (saved.getUserId() != null) {
            // Manual transaction flow: group by userId (best-effort, matches the admin check-in endpoint).
            List<Ticket> userTickets = ticketRepository.findByUserId(saved.getUserId());
            boolean anyIssuedRemaining = userTickets.stream().anyMatch(t -> t.getStatus() == TicketStatus.ISSUED);
            if (!anyIssuedRemaining) {
                eventPublisher.publishEvent(new TicketPurchaseCheckedInEvent(List.copyOf(userTickets)));
                log.debug("event=purchase_checked_in_all_used groupUserId={} ticketCount={}", saved.getUserId(), userTickets.size());
            } else {
                log.debug("event=purchase_checked_in_partial groupUserId={} remainingIssued=true", saved.getUserId());
            }
        }

        log.debug("event=ticket_checked_in_persisted ticketId={} qrCodeId={}", saved.getTicketId(), qrCodeId);
        return Optional.of(saved);
    }
}
