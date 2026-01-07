package com.prarambh.act.one.ticketing.service.email;

import com.prarambh.act.one.ticketing.model.EmailQuoteType;
import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.service.TicketPurchaseCheckedInEvent;
import com.prarambh.act.one.ticketing.service.TicketPurchaseIssuedEvent;
import com.prarambh.act.one.ticketing.service.card.TicketCardGenerator;
import com.prarambh.act.one.ticketing.service.quotes.QuoteSelectionService;
import com.prarambh.act.one.ticketing.service.quotes.TheatreQuote;

import java.time.format.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
       * Bounded pool size for CPU-heavy image generation.
       * Configurable via env var ACTONE_TICKET_CARD_MAX_PARALLEL (defaults to 3).
       */
      @Value("${actone.ticket-card.max-parallel:3}")
      private int maxParallelCardGen;

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

            long startNs = System.nanoTime();

            // Parallel, bounded, in-memory generation of JPG attachments.
            int threads = Math.max(1, Math.min(maxParallelCardGen, tickets.size()));
            log.info("event=purchase_issue_email_card_gen_start to={} ticketCount={} threads={} maxParallelCardGen={}",
                    to, tickets.size(), threads, maxParallelCardGen);
             ExecutorService pool = Executors.newFixedThreadPool(threads);
             try {
                   List<CompletableFuture<EmailSender.EmailAttachment>> futures = tickets.stream()
                           .map(t -> CompletableFuture.supplyAsync(() -> generateAttachmentWithRetry(t, 4), pool))
                           .toList();

                   // Wait for all; if any fails we abort (complete-only).
                   List<EmailSender.EmailAttachment> attachments = futures.stream().map(cf -> {
                         try {
                               // No artificial timeout (per your requirement). This will wait until done.
                               return cf.join();
                         } catch (Exception e) {
                               throw e;
                         }
                   }).toList();

                   long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
                   long totalBytes = attachments.stream().mapToLong(a -> a.bytes() == null ? 0 : a.bytes().length).sum();
                   log.info("event=purchase_issue_email_attachments_ready to={} attachmentCount={} totalBytes={} avgBytes={} tookMs={}",
                          to,
                          attachments.size(),
                          totalBytes,
                          attachments.isEmpty() ? 0 : (totalBytes / attachments.size()),
                          tookMs);

                   String body = buildIssuedBody(tickets);
                   log.info("event=purchase_issue_email_body_ready to={} bodyLength={}", to, body.length());

                  long sendStartNs = System.nanoTime();
                   emailSender.send(to, subject, body, attachments);
                  long sendTookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sendStartNs);
                  log.info("event=purchase_issue_email_sender_done to={} sendTookMs={} attachmentCount={} totalBytes={}",
                          to, sendTookMs, attachments.size(), totalBytes);
                   log.info("event=purchase_issue_email_complete to={} ticketCount={}", to, tickets.size());
             } catch (Exception e) {
                   log.error("event=purchase_issue_email_failed to={} ticketCount={} error={}", to, tickets.size(), e.toString(), e);
             } finally {
                   pool.shutdown();
             }
       }

      private EmailSender.EmailAttachment generateAttachmentWithRetry(Ticket t, int maxAttempts) {
            Exception last = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                  try {
                         long genStartNs = System.nanoTime();
                         byte[] jpg = ticketCardGenerator.generateTicketCardJpegBytes(t);
                         long genTookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - genStartNs);
                         if (jpg == null || jpg.length == 0) {
                               throw new IllegalStateException("Empty JPG bytes");
                         }
                         log.info("event=ticket_card_generated_bytes ticketId={} qrCodeId={} bytes={} tookMs={}",
                                t.getTicketId(), t.getQrCodeId(), jpg.length, genTookMs);
                         return EmailSender.EmailAttachment.fromBytes(
                                 "ticket-" + t.getTicketId() + ".jpg",
                                 "image/jpeg",
                                 jpg);
                  } catch (Exception e) {
                        last = e;
                        log.warn("event=ticket_card_generation_retry ticketId={} qrCodeId={} attempt={} error={}",
                                t.getTicketId(), t.getQrCodeId(), attempt, e.toString());
                        if (attempt < maxAttempts) {
                              try {
                                    Thread.sleep(150L * attempt);
                              } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                              }
                        }
                  }
            }
            log.error("event=ticket_card_generation_failed ticketId={} qrCodeId={} error={}", t.getTicketId(), t.getQrCodeId(), last != null ? last.toString() : "unknown", last);
            throw new IllegalStateException("Failed to generate ticket card after retries for ticketId=" + t.getTicketId(), last);
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