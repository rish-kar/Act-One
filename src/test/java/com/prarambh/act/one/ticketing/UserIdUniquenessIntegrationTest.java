package com.prarambh.act.one.ticketing;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.service.TicketIssuanceService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserIdUniquenessIntegrationTest {

    @Autowired
    private TicketIssuanceService ticketIssuanceService;

    @Test
    @Transactional
    void sameIdentityGetsSameUserId_differentIdentityGetsDifferentUserId() {
        Ticket t1 = new Ticket();
        t1.setFullName("Alice");
        t1.setEmail("alice@example.com");
        t1.setPhoneNumber("+91 99999 00000");
        t1.setShowName("Show");
        t1.setShowId("S1");
        t1.setTicketAmount(BigDecimal.TEN);
        t1.setTicketCount(1);

        Ticket t2 = new Ticket();
        t2.setFullName("Alice");
        t2.setEmail("alice@example.com");
        t2.setPhoneNumber("9999900000");
        t2.setShowName("Show");
        t2.setShowId("S1");
        t2.setTicketAmount(BigDecimal.TEN);
        t2.setTicketCount(1);

        Ticket t3 = new Ticket();
        t3.setFullName("Alice Changed");
        t3.setEmail("alice@example.com");
        t3.setPhoneNumber("9999900000");
        t3.setShowName("Show");
        t3.setShowId("S1");
        t3.setTicketAmount(BigDecimal.TEN);
        t3.setTicketCount(1);

        String id1 = ticketIssuanceService.issueTickets(t1).get(0).getUserId();
        String id2 = ticketIssuanceService.issueTickets(t2).get(0).getUserId();
        String id3 = ticketIssuanceService.issueTickets(t3).get(0).getUserId();

        assertThat(id1).isNotBlank();
        assertThat(id2).isEqualTo(id1);
        assertThat(id3).isNotEqualTo(id1);
    }
}

