package com.prarambh.act.one.ticketing;

import com.prarambh.act.one.ActOneApplication;
import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.service.ManualTransactionService;
import com.prarambh.act.one.ticketing.service.TicketIssuanceService;
import com.prarambh.act.one.ticketing.service.email.EmailSender;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.TicketCheckInService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        classes = ActOneApplication.class,
        properties = {
                "actone.email.enabled=true"
        }
)
@ActiveProfiles("test")
@Import(TicketIssuedEmailListenerTest.TestEmailConfig.class)
class TicketIssuedEmailListenerTest {

    @Configuration
    static class TestEmailConfig {
        @Bean
        TestEmailSender emailSender() {
            return new TestEmailSender();
        }
    }

    static class TestEmailSender implements EmailSender {
        private final List<String> deliveries = new ArrayList<>();

        @Override
        public synchronized void send(String to, String subject, String body) {
            deliveries.add(to + "|" + subject + "|" + body);
        }

        boolean awaitDeliveryCount(int expected, long timeoutMs) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (System.nanoTime() < deadline) {
                if (deliveryCount() >= expected) {
                    return true;
                }
                Thread.sleep(25);
            }
            return deliveryCount() >= expected;
        }

        synchronized int deliveryCount() {
            return deliveries.size();
        }

        synchronized void reset() {
            deliveries.clear();
        }

        synchronized String lastBody() {
            if (deliveries.isEmpty()) {
                return null;
            }
            String last = deliveries.get(deliveries.size() - 1);
            int first = last.indexOf('|');
            int second = last.indexOf('|', first + 1);
            return second >= 0 ? last.substring(second + 1) : null;
        }
    }

    @Autowired
    TicketIssuanceService ticketIssuanceService;

    @Autowired
    ManualTransactionService manualTransactionService;

    @Autowired
    TicketCheckInService ticketCheckInService;

    @Autowired
    TicketRepository ticketRepository;

    @Autowired
    TestEmailSender emailSender;

    @org.junit.jupiter.api.BeforeEach
    void resetEmailSender() {
        emailSender.reset();
    }

    @Test
    void sendsEmailAfterTicketIsIssued_whenEmailEnabledAndEmailPresent() throws Exception {
        Ticket t = new Ticket();
        t.setTicketId(UUID.randomUUID());
        t.setShowName("Test Show");
        t.setFullName("Test User");
        t.setEmail("test@example.com");
        t.setPhoneNumber("1000000000");
        t.setTransactionId("DEMO-TXN-1");
        t.setTicketAmount(new java.math.BigDecimal("750.00"));

        // issueTicket now creates ticket with TRANSACTION_MADE status - no email sent yet
        List<Ticket> tickets = ticketIssuanceService.issueTickets(t);
        org.junit.jupiter.api.Assertions.assertEquals(0, emailSender.deliveryCount());

        // After manual validation -> ISSUED, email is sent
        int customerId = tickets.get(0).getCustomerId();
        manualTransactionService.validateTransactionAndIssueTickets(customerId);

        org.junit.jupiter.api.Assertions.assertTrue(emailSender.awaitDeliveryCount(1, 8000));
        org.junit.jupiter.api.Assertions.assertEquals(1, emailSender.deliveryCount());
    }

    @Test
    void sendsOneEmailPerPurchase_whenEmailEnabledAndEmailPresent() throws Exception {
        Ticket t = new Ticket();
        t.setTicketId(UUID.randomUUID());
        t.setShowName("Test Show");
        t.setFullName("Test User");
        t.setEmail("test@example.com");
        t.setPhoneNumber("1000000000");
        t.setTicketCount(3);
        t.setTransactionId("DEMO-TXN-2");
        t.setTicketAmount(new java.math.BigDecimal("750.00"));

        // issueTickets now creates tickets with TRANSACTION_MADE status - no email sent yet
        List<Ticket> tickets = ticketIssuanceService.issueTickets(t);
        org.junit.jupiter.api.Assertions.assertEquals(0, emailSender.deliveryCount());

        // After manual validation -> ISSUED, one email is sent for the purchase
        int customerId = tickets.get(0).getCustomerId();
        manualTransactionService.validateTransactionAndIssueTickets(customerId);

        org.junit.jupiter.api.Assertions.assertTrue(emailSender.awaitDeliveryCount(1, 8000));
        org.junit.jupiter.api.Assertions.assertEquals(1, emailSender.deliveryCount());
    }

    @Test
    void doesNotSendEmail_whenEmailMissing() throws Exception {
        Ticket t = new Ticket();
        t.setTicketId(UUID.randomUUID());
        t.setShowName("Test Show");
        t.setFullName("Test User");
        t.setEmail(null);
        t.setPhoneNumber("1000000000");
        t.setTicketCount(3);
        t.setTransactionId("DEMO-TXN-3");
        t.setTicketAmount(new java.math.BigDecimal("750.00"));

        List<Ticket> tickets = ticketIssuanceService.issueTickets(t);

        // Validate the transaction
        int customerId = tickets.get(0).getCustomerId();
        manualTransactionService.validateTransactionAndIssueTickets(customerId);

        // Email should NOT be sent because email is missing.
        // Allow a short window for any async listeners; then assert nothing was sent.
        org.junit.jupiter.api.Assertions.assertFalse(emailSender.awaitDeliveryCount(1, 1500));
        org.junit.jupiter.api.Assertions.assertEquals(0, emailSender.deliveryCount());
    }

    private static String extractQuoteLine(String body) {
        if (body == null) {
            return null;
        }

        String[] markers = {"Quote of the day:", "Quote of the moment:"};
        for (String marker : markers) {
            int idx = body.indexOf(marker);
            if (idx < 0) {
                continue;
            }

            String after = body.substring(idx + marker.length());
            // Split into lines and return the first non-empty line.
            String[] lines = after.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line == null ? "" : line.trim();
                if (!trimmed.isBlank()) {
                    return trimmed;
                }
            }
            return null;
        }

        return null;
    }

    @Test
    void checkInEmailIsSentOnlyAfterLastTicketInGroupIsUsed() throws Exception {
        Ticket t = new Ticket();
        t.setShowName("Test Show");
        t.setFullName("Test User");
        t.setEmail("test@example.com");
        t.setPhoneNumber("1000000000");
        t.setTicketCount(3);
        t.setTransactionId("DEMO-TXN-4");
        t.setTicketAmount(new java.math.BigDecimal("750.00"));

        List<Ticket> tickets = ticketIssuanceService.issueTickets(t);

        // Validate the transaction to move to ISSUED
        int customerId = tickets.get(0).getCustomerId();
        List<Ticket> issued = manualTransactionService.validateTransactionAndIssueTickets(customerId);
        org.junit.jupiter.api.Assertions.assertTrue(emailSender.awaitDeliveryCount(1, 8000));
        org.junit.jupiter.api.Assertions.assertEquals(1, emailSender.deliveryCount());

        String issueBody = emailSender.lastBody();
        org.assertj.core.api.Assertions.assertThat(issueBody).contains("Ticket details:");
        org.assertj.core.api.Assertions.assertThat(issueBody).contains("Quote of the day:");

        // Reset so we only count check-in emails
        emailSender.reset();

        // check-in first two tickets -> no new email
        ticketCheckInService.checkInByBarcode(issued.get(0).getQrCodeId());
        org.junit.jupiter.api.Assertions.assertFalse(emailSender.awaitDeliveryCount(1, 300));

        ticketCheckInService.checkInByBarcode(issued.get(1).getQrCodeId());
        org.junit.jupiter.api.Assertions.assertFalse(emailSender.awaitDeliveryCount(1, 300));

        // check-in last ticket -> emits one email
        ticketCheckInService.checkInByBarcode(issued.get(2).getQrCodeId());
        org.junit.jupiter.api.Assertions.assertTrue(emailSender.awaitDeliveryCount(1, 8000));
        org.junit.jupiter.api.Assertions.assertEquals(1, emailSender.deliveryCount());

        String body = emailSender.lastBody();
        org.assertj.core.api.Assertions.assertThat(body).contains("Your check-in is confirmed");
        org.assertj.core.api.Assertions.assertThat(body).contains("Quote of the moment:");
        for (Ticket it : issued) {
            org.assertj.core.api.Assertions.assertThat(body).contains(it.getQrCodeId());
        }

        // Quote text is optional; we only assert the section header exists.

        // Sanity: no ISSUED tickets remain for group
        long remaining = ticketRepository.countByEmailIgnoreCaseAndPhoneNumberIgnoreCaseAndShowIdAndStatus(
                t.getEmail(), t.getPhoneNumber(), issued.get(0).getShowId(), TicketStatus.ISSUED);
        org.junit.jupiter.api.Assertions.assertEquals(0, remaining);
    }
}
