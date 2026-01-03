package com.prarambh.act.one.ticketing.service.email;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.service.TicketIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Legacy per-ticket email listener.
 *
 * <p>Kept for compatibility but disabled to prevent multiple emails per purchase.
 * Purchase-level emails are sent by {@link TicketPurchaseEmailListener}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketIssuedEmailListener {

    private final EmailSender emailSender;
    private final EmailProperties emailProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketIssued(TicketIssuedEvent event) {
        // Disabled: avoid sending one email per ticket row.
        // The event now contains a list; log the first ticket id if present.
        var firstId = event.getTickets() == null || event.getTickets().isEmpty()
                ? null
                : event.getTickets().get(0).getTicketId();
        log.debug("event=ticket_issue_email_skipped reason=use_purchase_email_listener ticketId={}", firstId);
    }

    private String buildBody(Ticket t) {
        String fromName = emailProperties.fromName() == null || emailProperties.fromName().isBlank()
                ? "Act-One"
                : emailProperties.fromName();

        // Simple plaintext body (safe default). Can be replaced by Thymeleaf template later.
        return "Hello " + nullToEmpty(t.getFullName()) + ",\n\n"
                + "Your ticket has been issued." + "\n\n"
                + "Show: " + nullToEmpty(t.getShowName()) + "\n"
                + "Ticket ID: " + t.getTicketId() + "\n"
                + "Barcode ID: " + nullToEmpty(t.getQrCodeId()) + "\n\n"
                + "Thanks,\n"
                + fromName + "\n";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
