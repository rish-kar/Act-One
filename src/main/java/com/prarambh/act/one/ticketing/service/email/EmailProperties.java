package com.prarambh.act.one.ticketing.service.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for outgoing email. */
@ConfigurationProperties(prefix = "actone.email")
public record EmailProperties(
        boolean enabled,
        String subject,
        String fromName,
        String from
) {
}

