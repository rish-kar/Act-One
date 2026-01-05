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
class UserIdIdentityStrictnessIntegrationTest {

    @Autowired
    private TicketIssuanceService ticketIssuanceService;

    @Test
    @Transactional
    void transactionIdDoesNotAffectUserId_nameChangeMustCreateNewUserId() {
        Ticket t1 = new Ticket();
        t1.setFullName("ABCD Kar");
        t1.setEmail("rishabh.kar.ai@gmail.com");
        t1.setPhoneNumber("9916604905");
        t1.setShowName("Show");
        t1.setShowId("S1");
        t1.setTicketAmount(BigDecimal.TEN);
        t1.setTicketCount(1);
        t1.setTransactionId("DDDD");

        Ticket t2 = new Ticket();
        t2.setFullName("ABCD EKar");
        t2.setEmail("rishabh.kar.ai@gmail.com");
        t2.setPhoneNumber("9916604905");
        t2.setShowName("Show");
        t2.setShowId("S1");
        t2.setTicketAmount(BigDecimal.TEN);
        t2.setTicketCount(1);
        t2.setTransactionId("DDDD");

        String id1 = ticketIssuanceService.issueTickets(t1).get(0).getUserId();
        String id2 = ticketIssuanceService.issueTickets(t2).get(0).getUserId();

        assertThat(id1).isNotBlank();
        assertThat(id2).isNotBlank();
        assertThat(id2).isNotEqualTo(id1);
    }
}

