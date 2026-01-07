package com.prarambh.act.one.ticketing.service.email;

import java.nio.file.Path;
import java.util.List;

/** Contract for sending emails (SMTP, test double, or future provider integration). */
public interface EmailSender {

    void send(String to, String subject, String body);

    /**
     * Sends an email with attachments.
     *
     * <p>Default implementation falls back to a plain email.
     */
    default void send(String to, String subject, String body, List<EmailAttachment> attachments) {
        send(to, subject, body);
    }

    /**
     * Attachment which can either reference a file on disk (path) or hold bytes in memory.
     * Exactly one of {@code path} or {@code bytes} should be non-null.
     */
    record EmailAttachment(String filename, String contentType, Path path, byte[] bytes) {
        public static EmailAttachment fromPath(String filename, String contentType, Path path) {
            return new EmailAttachment(filename, contentType, path, null);
        }

        public static EmailAttachment fromBytes(String filename, String contentType, byte[] bytes) {
            return new EmailAttachment(filename, contentType, null, bytes);
        }
    }
}
