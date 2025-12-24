package com.prarambh.act.one.ticketing;

import com.prarambh.act.one.ActOneApplication;
import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.service.card.TicketCardGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

    @TempDir
    Path tempDir;

    @Autowired
    TicketCardGenerator ticketCardGenerator;

    @Test
    void generatesPngFile_andFileIsNonEmpty() throws Exception {
        // Point generator to our unique temp dir for this test.
        System.setProperty("actone.ticket-card.outputDir", tempDir.toString());
        try {
            Ticket t = new Ticket();
            t.setTicketId(UUID.randomUUID());
            t.setShowName("Test Show");
            t.setFullName("Test User");
            t.setEmail("test@example.com");
            t.setPhoneNumber("+10000000000");
            t.setStatus(TicketStatus.ISSUED);
            // Provide a sample 18-character barcodeId.
            t.setBarcodeId("TEST12345678901234");

            Path generated = ticketCardGenerator.generateTicketCardPng(t);
            Assertions.assertNotNull(generated);
            Assertions.assertTrue(Files.exists(generated), "Expected PNG file to exist at " + generated);
            Assertions.assertTrue(Files.size(generated) > 0, "Expected PNG file to be non-empty");

            // PNG signature: 89 50 4E 47 0D 0A 1A 0A
            byte[] bytes = Files.readAllBytes(generated);
            Assertions.assertTrue(bytes.length >= 8);
            Assertions.assertEquals((byte) 0x89, bytes[0]);
            Assertions.assertEquals((byte) 0x50, bytes[1]);
            Assertions.assertEquals((byte) 0x4E, bytes[2]);
            Assertions.assertEquals((byte) 0x47, bytes[3]);
        } finally {
            System.clearProperty("actone.ticket-card.outputDir");
        }
    }
}
