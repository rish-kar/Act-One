package com.prarambh.act.one.ticketing.service.email;

import com.prarambh.act.one.ticketing.model.EmailQuoteType;
import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.service.TicketPurchaseCheckedInEvent;
import com.prarambh.act.one.ticketing.service.TicketPurchaseIssuedEvent;
import com.prarambh.act.one.ticketing.service.card.TicketCardGenerator;
import com.prarambh.act.one.ticketing.service.quotes.QuoteSelectionService;
import com.prarambh.act.one.ticketing.service.quotes.TheatreQuote;
import java.nio.file.Path;
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
      private final TicketCardGenerator ticketCardGenerator;
      private final QuoteSelectionService quoteSelectionService;

      /**
       * Listener that sends an email after tickets are issued. Runs asynchronously after commit.
       *
       * @param event purchase issued event
       */
      @Async
      @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
      public void onPurchaseIssued(TicketPurchaseIssuedEvent event) {
            if (!emailProperties.enabled()) {
                  log.debug("event=purchase_issue_email_skipped reason=disabled");
                  return;
            }

            List<Ticket> tickets = event.getTickets();
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

            // Generate ticket cards sequentially to avoid thread pool issues in cloud environments
            List<EmailSender.EmailAttachment> attachments = new java.util.ArrayList<>();
            for (Ticket t : tickets) {
                try {
                    Path imgPath = ticketCardGenerator.generateTicketCardPng(t);
                    if (imgPath != null) {
                        attachments.add(new EmailSender.EmailAttachment("ticket-" + t.getTicketId() + ".jpg", "image/jpeg", imgPath));
                    }
                } catch (Exception e) {
                    log.error("event=ticket_card_generation_failed ticketId={} qrCodeId={} error={}", t.getTicketId(), t.getQrCodeId(), e.toString(), e);
                }
            }

            log.info("event=purchase_issue_email_attachments_ready to={} attachmentCount={}", to, attachments.size());

            try {
                  String body = buildIssuedBody(tickets);
                  log.info("event=purchase_issue_email_body_ready to={} bodyLength={}", to, body.length());
                  emailSender.send(to, subject, body, attachments);
                  log.info("event=purchase_issue_email_complete to={} ticketCount={}", to, tickets.size());
            } catch (Exception e) {
                  log.error("event=purchase_issue_email_failed to={} ticketCount={} error={}", to, tickets.size(), e.toString(), e);
            }
      }

      /**
       * Listener that sends check-in confirmation emails after a purchase group is fully used.
       *
       * @param event check-in event
       */
      @Async
      @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
      public void onPurchaseCheckedIn(TicketPurchaseCheckedInEvent event) {
            if (!emailProperties.enabled()) {
                  log.debug("event=purchase_checkin_email_skipped reason=disabled");
                  return;
            }

            List<Ticket> tickets = event.getTickets();
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

            String subject = "Checked-in confirmed - enjoy the show";
            log.info("event=purchase_checkin_email_attempt to={} ticketCount={} firstTicketId={}", to, tickets.size(), first.getTicketId());
            emailSender.send(to, subject, buildCheckedInBody(tickets));
      }

      private String buildIssuedBody(List<Ticket> tickets) {
            Ticket first = tickets.get(0);

            TheatreQuote quote = quoteSelectionService.quoteForUser(first.getUserId(), EmailQuoteType.ISSUE);

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
                          .append(" | QR code ID: ").append(nullToEmpty(t.getQrCodeId()))
                          .append("\n");
            }

            sb.append("\nQuote of the day:\n");
            if (quote != null && quote.text() != null && !quote.text().isBlank()) {
                  sb.append(quote.formatted()).append("\n\n");
            }

            sb.append("We can't wait to host you - enjoy the show!\n\n")
                    .append("Thanks,\n")
                    .append("Prarambh Theatre Group")
                    .append("\n");

            return sb.toString();
      }

      private String buildCheckedInBody(List<Ticket> tickets) {
            Ticket first = tickets.get(0);

            TheatreQuote quote = quoteSelectionService.quoteForUser(first.getUserId(), EmailQuoteType.CHECK_IN);

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
                          .append(" | QR code ID: ").append(nullToEmpty(t.getQrCodeId()))
                          .append("\n");
            }

            sb.append("\nQuote of the moment:\n");
            if (quote != null && quote.text() != null && !quote.text().isBlank()) {
                  sb.append(quote.formatted()).append("\n\n");
            }

            sb.append("Have a wonderful time - enjoy the show!\n\n")
                    .append("Warmly,\n")
                    .append("Prarambh Theatre Group")
                    .append("\n");

            return sb.toString();
      }

      private static String nullToEmpty(String s) {
            return s == null ? "" : s;
      }
}