package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.EmailQuoteType;
import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.card.TicketCardGenerator;
import com.prarambh.act.one.ticketing.service.email.EmailProperties;
import com.prarambh.act.one.ticketing.service.email.EmailSender;
import com.prarambh.act.one.ticketing.service.quotes.QuoteSelectionService;
import com.prarambh.act.one.ticketing.service.quotes.TheatreQuote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Controller for resending ticket issue emails.
 */
@RestController
@RequestMapping("/api/resend-email")
@RequiredArgsConstructor
@Slf4j
public class ResendEmailController {

    private final TicketRepository ticketRepository;
    private final TicketCardGenerator ticketCardGenerator;
    private final EmailSender emailSender;
    private final EmailProperties emailProperties;
    private final QuoteSelectionService quoteSelectionService;

    @Value("${actone.admin.purge-password:}")
    private String adminPassword;

    @Value("${actone.ticket-card.max-parallel:3}")
    private int maxParallelCardGen;

    private boolean isAdmin(String pass) {
        return StringUtils.hasText(adminPassword)
                && StringUtils.hasText(pass)
                && pass.equals(adminPassword);
    }

    /**
     * Trigger ticket issue email by user full name and transaction ID.
     * Only sends email if ticket status is ISSUED.
     * Generates ticket cards and sends email with attachments like the original issue email.
     *
     * @param fullName user full name
     * @param transactionId transaction ID
     * @param pass admin password header
     * @return response with status
     */
    @PostMapping("/by-name-and-transaction")
    public ResponseEntity<?> resendEmailByNameAndTransaction(
            @RequestParam String fullName,
            @RequestParam String transactionId,
            @RequestHeader(name = "X-Admin-Password", required = false) String pass
    ) {
        if (!isAdmin(pass)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "admin password required"));
        }

        if (!StringUtils.hasText(fullName) || !StringUtils.hasText(transactionId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "fullName and transactionId are required"));
        }

        if (!emailProperties.enabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Email sending is disabled"));
        }

        // Find tickets by transactionId
        List<Ticket> tickets = ticketRepository.findByTransactionId(transactionId.trim());

        if (tickets.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No tickets found for transactionId", "transactionId", transactionId));
        }

        // Verify full name matches (case-insensitive)
        boolean nameMatches = tickets.stream()
                .anyMatch(t -> t.getFullName() != null && t.getFullName().equalsIgnoreCase(fullName.trim()));

        if (!nameMatches) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No tickets found matching fullName and transactionId",
                            "fullName", fullName, "transactionId", transactionId));
        }

        // Filter tickets that match the full name
        List<Ticket> matchingTickets = tickets.stream()
                .filter(t -> t.getFullName() != null && t.getFullName().equalsIgnoreCase(fullName.trim()))
                .toList();

        // Check if all matching tickets have ISSUED status
        boolean allIssued = matchingTickets.stream()
                .allMatch(t -> t.getStatus() == TicketStatus.ISSUED);

        if (!allIssued) {
            List<String> statuses = matchingTickets.stream()
                    .map(t -> t.getTicketId() + "=" + t.getStatus())
                    .toList();
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Not all tickets are in ISSUED status. Email not sent.",
                            "ticketStatuses", statuses));
        }

        Ticket first = matchingTickets.get(0);
        String to = first.getEmail();
        if (to == null || to.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "No email address found for the tickets"));
        }

        // Trigger async email sending with card generation
        sendEmailAsync(matchingTickets, to);

        log.info("event=resend_email_triggered fullName={} transactionId={} ticketCount={} to={}",
                fullName, transactionId, matchingTickets.size(), to);

        return ResponseEntity.ok(Map.of(
                "message", "Email sending triggered successfully",
                "fullName", fullName,
                "transactionId", transactionId,
                "ticketCount", matchingTickets.size(),
                "email", to
        ));
    }

    @Async
    void sendEmailAsync(List<Ticket> tickets, String to) {
        Ticket first = tickets.get(0);

        String subject = (emailProperties.subject() == null || emailProperties.subject().isBlank())
                ? "Your tickets have been issued"
                : emailProperties.subject();

        log.info("event=resend_email_start to={} ticketCount={} firstTicketId={}", to, tickets.size(), first.getTicketId());

        long startNs = System.nanoTime();

        // Parallel, bounded, in-memory generation of JPG attachments.
        int threads = Math.max(1, Math.min(maxParallelCardGen, tickets.size()));
        log.info("event=resend_email_card_gen_start to={} ticketCount={} threads={}", to, tickets.size(), threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<CompletableFuture<EmailSender.EmailAttachment>> futures = tickets.stream()
                    .map(t -> CompletableFuture.supplyAsync(() -> generateAttachmentWithRetry(t, 4), pool))
                    .toList();

            // Wait for all; if any fails we abort.
            List<EmailSender.EmailAttachment> attachments = futures.stream().map(cf -> {
                try {
                    return cf.join();
                } catch (Exception e) {
                    throw e;
                }
            }).toList();

            long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            long totalBytes = attachments.stream().mapToLong(a -> a.bytes() == null ? 0 : a.bytes().length).sum();
            log.info("event=resend_email_attachments_ready to={} attachmentCount={} totalBytes={} tookMs={}",
                    to, attachments.size(), totalBytes, tookMs);

            String body = buildIssuedBody(tickets);
            log.info("event=resend_email_body_ready to={} bodyLength={}", to, body.length());

            long sendStartNs = System.nanoTime();
            emailSender.send(to, subject, body, attachments);
            long sendTookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sendStartNs);
            log.info("event=resend_email_sender_done to={} sendTookMs={} attachmentCount={} totalBytes={}",
                    to, sendTookMs, attachments.size(), totalBytes);
            log.info("event=resend_email_complete to={} ticketCount={}", to, tickets.size());
        } catch (Exception e) {
            log.error("event=resend_email_failed to={} ticketCount={} error={}", to, tickets.size(), e.toString(), e);
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
                log.info("event=resend_ticket_card_generated ticketId={} qrCodeId={} bytes={} tookMs={}",
                        t.getTicketId(), t.getQrCodeId(), jpg.length, genTookMs);
                return EmailSender.EmailAttachment.fromBytes(
                        "ticket-" + t.getTicketId() + ".jpg",
                        "image/jpeg",
                        jpg);
            } catch (Exception e) {
                last = e;
                log.warn("event=resend_ticket_card_generation_retry ticketId={} qrCodeId={} attempt={} error={}",
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
        log.error("event=resend_ticket_card_generation_failed ticketId={} qrCodeId={} error={}",
                t.getTicketId(), t.getQrCodeId(), last != null ? last.toString() : "unknown", last);
        throw new IllegalStateException("Failed to generate ticket card after retries for ticketId=" + t.getTicketId(), last);
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

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

