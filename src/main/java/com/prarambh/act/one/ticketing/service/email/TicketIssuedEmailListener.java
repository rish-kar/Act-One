package com.prarambh.act.one.ticketing.service.email;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.service.TicketIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends a confirmation email after a ticket is issued and the transaction commits. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketIssuedEmailListener {

    private final EmailSender emailSender;
    private final EmailProperties emailProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketIssued(TicketIssuedEvent event) {
        if (!emailProperties.enabled()) {
            log.debug("event=ticket_issue_email_skipped reason=disabled");
            return;
        }

        Ticket t = event.ticket();
        String to = t.getEmail();
        if (to == null || to.isBlank()) {
            log.info("event=ticket_issue_email_skipped reason=missing_email ticketId={}", t.getTicketId());
            return;
        }

        String subject = emailProperties.subject() == null || emailProperties.subject().isBlank()
                ? "Your ticket has been issued"
                : emailProperties.subject();

        log.info("event=ticket_issue_email_attempt ticketId={} to={} subject={}", t.getTicketId(), to, subject);

        String body = buildBody(t);
        emailSender.send(to, subject, body);
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
                + "Barcode ID: " + nullToEmpty(t.getBarcodeId()) + "\n\n"
                + "Thanks,\n"
                + fromName + "\n";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
