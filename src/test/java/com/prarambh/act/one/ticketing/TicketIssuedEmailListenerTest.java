package com.prarambh.act.one.ticketing;

import com.prarambh.act.one.ActOneApplication;
import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.service.TicketIssuanceService;
import com.prarambh.act.one.ticketing.service.email.EmailSender;
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
        public void send(String to, String subject, String body) {
            deliveries.add(to + "|" + subject);
        }

        boolean awaitDeliveryCount(int expected, long timeoutMs) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (System.nanoTime() < deadline) {
                if (deliveries.size() >= expected) {
                    return true;
                }
                Thread.sleep(25);
            }
            return deliveries.size() >= expected;
        }

        int deliveryCount() {
            return deliveries.size();
        }
    }

    @Autowired
    TicketIssuanceService ticketIssuanceService;

    @Autowired
    TestEmailSender emailSender;

    @Test
    void sendsEmailAfterTicketIsIssued_whenEmailEnabledAndEmailPresent() throws Exception {
        Ticket t = new Ticket();
        t.setTicketId(UUID.randomUUID());
        t.setShowName("Test Show");
        t.setFullName("Test User");
        t.setEmail("test@example.com");
        t.setPhoneNumber("+10000000000");

        ticketIssuanceService.issueTicket(t);

        org.junit.jupiter.api.Assertions.assertTrue(emailSender.awaitDeliveryCount(1, 2000));
        org.junit.jupiter.api.Assertions.assertEquals(1, emailSender.deliveryCount());
    }

    @Test
    void doesNotSendEmail_whenEmailMissing() throws Exception {
        Ticket t = new Ticket();
        t.setTicketId(UUID.randomUUID());
        t.setShowName("Test Show");
        t.setFullName("Test User");
        t.setEmail(null);
        t.setPhoneNumber("+10000000000");

        ticketIssuanceService.issueTicket(t);

        org.junit.jupiter.api.Assertions.assertFalse(emailSender.awaitDeliveryCount(1, 400));
        org.junit.jupiter.api.Assertions.assertEquals(0, emailSender.deliveryCount());
    }
}
