package com.prarambh.act.one.ticketing;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.model.TicketStatus;
import com.prarambh.act.one.ticketing.service.card.TicketCardGenerator;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Simple manual runner to generate a single ticket PNG for visual inspection.
 * Not a test.
 */
public final class BarcodePngSmokeRunner {

    private BarcodePngSmokeRunner() {
    }

    public static void main(String[] args) {
        // Enable ticket card generation and force output to project root.
        System.setProperty("actone.ticket-card.enabled", "true");
        System.setProperty("actone.ticket-card.outputDir", ".");

        org.springframework.boot.builder.SpringApplicationBuilder app = new org.springframework.boot.builder.SpringApplicationBuilder(com.prarambh.act.one.ActOneApplication.class)
                .profiles("test")
                .logStartupInfo(false);

        try (org.springframework.context.ConfigurableApplicationContext ctx = app.run()) {
            TicketCardGenerator generator = ctx.getBean(TicketCardGenerator.class);

            Ticket t = new Ticket();
            t.setTicketId(UUID.randomUUID());
            t.setShowName("Test Show");
            t.setFullName("Barcode Smoke");
            t.setEmail("test@example.com");
            t.setPhoneNumber("9999999999");
            t.setStatus(TicketStatus.ISSUED);
            t.setQrCodeId("TEST12345678901234");

            Path p = generator.generateTicketCardPng(t);
            System.out.println("Generated: " + p.toAbsolutePath());
            System.out.println("Barcode value: " + t.getQrCodeId());
        }
    }
}
