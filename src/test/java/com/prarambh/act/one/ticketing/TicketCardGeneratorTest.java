package com.prarambh.act.one.ticketing;

import com.prarambh.act.one.ActOneApplication;
import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.service.card.TicketCardGenerator;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        classes = ActOneApplication.class,
        properties = {
                "actone.ticket-card.enabled=true"
        }
)
@ActiveProfiles("test")
class TicketCardGeneratorTest {

     @Autowired
     TicketCardGenerator ticketCardGenerator;

     @Test
     void generatesJpegBytes_andBytesAreNonEmpty() {
        Ticket t = new Ticket();
        t.setTicketId(UUID.randomUUID());
        t.setShowName("Test Show");
        t.setFullName("Test User");
        t.setEmail("test@example.com");
        t.setPhoneNumber("+10000000000");
        t.setQrCodeId("TEST12345678901234");

        byte[] jpg = ticketCardGenerator.generateTicketCardJpegBytes(t);
        Assertions.assertNotNull(jpg);
        Assertions.assertTrue(jpg.length > 1000, "Expected JPG to be non-trivially sized");

        // JPEG SOI marker: FF D8
        Assertions.assertEquals((byte) 0xFF, jpg[0]);
        Assertions.assertEquals((byte) 0xD8, jpg[1]);
     }
 }
