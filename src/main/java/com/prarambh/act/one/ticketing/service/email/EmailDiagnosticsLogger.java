package com.prarambh.act.one.ticketing.service.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Logs effective email/SMTP configuration on startup (no secrets). */
@Component
@Slf4j
public class EmailDiagnosticsLogger {

    private final EmailProperties emailProperties;

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${spring.mail.port:}")
    private String port;

    @Value("${spring.mail.username:}")
    private String username;

    public EmailDiagnosticsLogger(EmailProperties emailProperties) {
        this.emailProperties = emailProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logConfig() {
        log.info(
                "event=email_config enabled={} smtpHost={} smtpPort={} smtpUser={} from={} subject={}",
                emailProperties.enabled(),
                emptyToNull(host),
                emptyToNull(port),
                maskEmail(username),
                maskEmail(emailProperties.from()),
                emptyToNull(emailProperties.subject()));
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}

