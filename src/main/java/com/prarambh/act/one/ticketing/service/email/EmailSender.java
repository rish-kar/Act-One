package com.prarambh.act.one.ticketing.service.email;

import java.nio.file.Path;
import java.util.List;

/** Contract for sending emails (SMTP, test double, or future provider integration). */
public interface EmailSender {

    void send(String to, String subject, String body);

    /**
     * Sends an email with file attachments.
     *
     * <p>Default implementation falls back to a plain email.
     */
    default void send(String to, String subject, String body, List<EmailAttachment> attachments) {
        send(to, subject, body);
    }

    record EmailAttachment(String filename, String contentType, Path path) {
    }
}
