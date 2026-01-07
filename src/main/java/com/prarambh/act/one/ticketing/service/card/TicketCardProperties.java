package com.prarambh.act.one.ticketing.service.card;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for generated ticket card images. */
@ConfigurationProperties(prefix = "actone.ticket-card")
public record TicketCardProperties(
        /** If false, skip generation and attachments. */
        boolean enabled,
         /** Classpath resource path to the template image. */
         String templateResource
) {
}
