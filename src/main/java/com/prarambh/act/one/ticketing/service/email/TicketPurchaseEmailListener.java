package com.prarambh.act.one.ticketing.service.email;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.service.TicketPurchaseCheckedInEvent;
import com.prarambh.act.one.ticketing.service.TicketPurchaseIssuedEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends one email per purchase (issue/check-in), aggregating all ticket details. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketPurchaseEmailListener {

    private static final DateTimeFormatter IST_12H_TIME = DateTimeFormatter.ofPattern("hh:mm a");

    private final EmailSender emailSender;
    private final EmailProperties emailProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPurchaseIssued(TicketPurchaseIssuedEvent event) {
        if (!emailProperties.enabled()) {
            log.debug("event=purchase_issue_email_skipped reason=disabled");
            return;
        }

        List<Ticket> tickets = event.tickets();
        if (tickets == null || tickets.isEmpty()) {
            log.debug("event=purchase_issue_email_skipped reason=no_tickets");
            return;
        }

        Ticket first = tickets.get(0);
        String to = first.getEmail();
        if (to == null || to.isBlank()) {
            log.info("event=purchase_issue_email_skipped reason=missing_email ticketId={}", first.getTicketId());
            return;
        }

        String subject = (emailProperties.subject() == null || emailProperties.subject().isBlank())
                ? "Your tickets have been issued"
                : emailProperties.subject();

        log.info("event=purchase_issue_email_attempt to={} ticketCount={} firstTicketId={}", to, tickets.size(), first.getTicketId());
        emailSender.send(to, subject, buildIssuedBody(tickets));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPurchaseCheckedIn(TicketPurchaseCheckedInEvent event) {
        if (!emailProperties.enabled()) {
            log.debug("event=purchase_checkin_email_skipped reason=disabled");
            return;
        }

        List<Ticket> tickets = event.tickets();
        if (tickets == null || tickets.isEmpty()) {
            log.debug("event=purchase_checkin_email_skipped reason=no_tickets");
            return;
        }

        Ticket first = tickets.get(0);
        String to = first.getEmail();
        if (to == null || to.isBlank()) {
            log.info("event=purchase_checkin_email_skipped reason=missing_email ticketId={}", first.getTicketId());
            return;
        }

        String subject = "Checked-in confirmed — enjoy the show";
        log.info("event=purchase_checkin_email_attempt to={} ticketCount={} firstTicketId={}", to, tickets.size(), first.getTicketId());
        emailSender.send(to, subject, buildCheckedInBody(tickets));
    }

    private String buildIssuedBody(List<Ticket> tickets) {
        Ticket first = tickets.get(0);
        String fromName = effectiveFromName();

        StringBuilder sb = new StringBuilder();
        sb.append("Hello ").append(nullToEmpty(first.getFullName())).append(",\n\n")
                .append("Your ticket");
        if (tickets.size() > 1) {
            sb.append("s have");
        } else {
            sb.append(" has");
        }
        sb.append(" been issued successfully.\n\n")
                .append("Show: ").append(nullToEmpty(first.getShowName())).append("\n")
                .append("Tickets: ").append(tickets.size()).append("\n\n")
                .append("Ticket details:\n");

        for (int i = 0; i < tickets.size(); i++) {
            Ticket t = tickets.get(i);
            sb.append(i + 1).append(") Ticket ID: ").append(t.getTicketId())
                    .append(" | Barcode ID: ").append(nullToEmpty(t.getBarcodeId()))
                    .append("\n");
        }

        sb.append("\nQuote of the day:\n")
                .append("\"The stage is not merely the meeting place of all the arts, but is also the return of art to life.\" — Oscar Wilde\n\n")
                .append("We can’t wait to host you — enjoy the show!\n\n")
                .append("Thanks,\n")
                .append("Prarambh Theatre Group")
                .append("\n");

        return sb.toString();
    }

    private String buildCheckedInBody(List<Ticket> tickets) {
        Ticket first = tickets.get(0);
        String fromName = effectiveFromName();

        String usedAtDate = first.getUsedAtDate() != null ? first.getUsedAtDate().toString() : "";
        String usedAtTime = first.getUsedAtTime() != null ? first.getUsedAtTime().format(IST_12H_TIME) : "";

        StringBuilder sb = new StringBuilder();
        sb.append("Hello ").append(nullToEmpty(first.getFullName())).append(",\n\n")
                .append("Your check-in is confirmed.\n\n")
                .append("Show: ").append(nullToEmpty(first.getShowName())).append("\n")
                .append("Checked-in at: ").append(usedAtDate);
        if (!usedAtTime.isBlank()) {
            sb.append(" ").append(usedAtTime).append(" IST");
        }
        sb.append("\n\n")
                .append("Checked-in ticket details:\n");

        for (int i = 0; i < tickets.size(); i++) {
            Ticket t = tickets.get(i);
            sb.append(i + 1).append(") Ticket ID: ").append(t.getTicketId())
                    .append(" | Barcode ID: ").append(nullToEmpty(t.getBarcodeId()))
                    .append("\n");
        }

        sb.append("\nQuote of the moment:\n")
                .append("\"The mission of the theatre, after all, is to change, to raise the consciousness of people to their human possibilities.\" – Arthur Miller\n\n")
                .append("Have a wonderful time — enjoy the show!\n\n")
                .append("Warmly,\n")
                .append("Prarambh Theatre Group")
                .append("\n");

        return sb.toString();
    }

    private String effectiveFromName() {
        return (emailProperties.fromName() == null || emailProperties.fromName().isBlank())
                ? "Act-One"
                : emailProperties.fromName();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
