package com.prarambh.act.one.ticketing.service.email;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends emails via SMTP using Spring's JavaMailSender.
 *
 * <p>This service composes and sends plain-text emails (and emails with attachments) using the
 * configured JavaMailSender. It performs basic validation of SMTP configuration and message
 * recipients before attempting to send messages. The implementation attempts to use UTF-8 as the
 * message charset and normalizes certain unicode characters that are known to be problematic when
 * passing through intermediate mail systems.
 *
 * <p>If the first send attempt fails, a retry is scheduled after 120 seconds. Only one retry is attempted.
 *
 * <p>Logging: detailed events are logged for successful sends and failures. Sensitive values such as
 * SMTP username and addresses are masked in logs.
 */
@Service
@Slf4j
public class SmtpEmailSender implements EmailSender {

    private static final int RETRY_DELAY_SECONDS = 120;
    private static final int MAX_ATTEMPTS = 3;

    private final org.springframework.mail.javamail.JavaMailSender mailSender;
    private final EmailProperties emailProperties;
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor();

    public SmtpEmailSender(org.springframework.mail.javamail.JavaMailSender mailSender, EmailProperties emailProperties) {
        this.mailSender = mailSender;
        this.emailProperties = emailProperties;
    }

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${spring.mail.port:}")
    private String port;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private boolean smtpAuth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}")
    private boolean startTls;

    @Value("${actone.email.from:}")
    private String configuredFrom;

    @Value("${actone.email.bcc:}")
    private String configuredBcc;

    /**
     * Send a plain-text email to a single recipient.
     *
     * <p>The method validates that a recipient is provided and that SMTP host and authentication
     * details (when required) are available. It sets the message subject and body ensuring UTF-8
     * charset is used. If a configured BCC exists it will be applied. Errors are logged and do not
     * throw exceptions to callers (send failures are recorded in logs).
     *
     * <p>If the first attempt fails, a retry is scheduled after 120 seconds.
     *
     * @param to destination email address. If null or blank the send is skipped.
     * @param subject message subject; may be null.
     * @param body plain-text message body; may be null.
     */
    @Override
    public void send(String to, String subject, String body) {
        doSendPlain(to, subject, body, 1);
    }

    private void doSendPlain(String to, String subject, String body, int attempt) {
        if (to == null || to.isBlank()) {
            log.info("event=email_send_skipped reason=missing_to attempt={}", attempt);
            return;
        }

        if (host == null || host.isBlank()) {
            log.error("event=email_send_failed reason=smtp_host_missing to={} subject={} attempt={}", to, subject, attempt);
            return;
        }

        if (smtpAuth) {
            if (smtpUsername == null || smtpUsername.isBlank()) {
                log.error("event=email_send_failed reason=smtp_username_missing smtpHost={} smtpPort={} to={} subject={} attempt={}", host, port, to, subject, attempt);
                return;
            }
            if (smtpPassword == null || smtpPassword.isBlank()) {
                log.error("event=email_send_failed reason=smtp_password_missing smtpHost={} smtpPort={} smtpUser={} to={} subject={} attempt={}", host, port, maskEmail(smtpUsername), to, subject, attempt);
                return;
            }
        }

        String effectiveFrom = (configuredFrom != null && !configuredFrom.isBlank()) ? configuredFrom : smtpUsername;
        String bcc = (configuredBcc != null && !configuredBcc.isBlank()) ? configuredBcc.trim() : null;
        String fromName = emailProperties.fromName();

        try {
            var mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());

            if (effectiveFrom != null && !effectiveFrom.isBlank()) {
                if (fromName != null && !fromName.isBlank()) {
                    helper.setFrom(effectiveFrom, fromName);
                } else {
                    helper.setFrom(effectiveFrom);
                }
            }
            helper.setTo(to);
            if (bcc != null) {
                helper.setBcc(bcc);
            }
            String safeSubject = normalizeToAscii(subject == null ? "" : subject);
            helper.setSubject(safeSubject);
            mime.setSubject(safeSubject, StandardCharsets.UTF_8.name());

            String safeBody = normalizeToAscii(body == null ? "" : body);
            mime.setText(safeBody, StandardCharsets.UTF_8.name());

            // Force UTF-8 content type for plain text.
            mime.setHeader("Content-Type", "text/plain; charset=UTF-8");

            mailSender.send(mime);
            log.info(
                    "event=email_sent status=SUCCESS to={} bcc={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} charset={} attempt={}",
                    to,
                    maskEmail(bcc),
                    subject,
                    host,
                    port,
                    maskEmail(smtpUsername),
                    maskEmail(effectiveFrom),
                    smtpAuth,
                    startTls,
                    StandardCharsets.UTF_8.name(),
                    attempt);
        } catch (MailException e) {
            log.error(
                    "event=email_send_failed status=FAILED to={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} errorType={} error={} attempt={}",
                    to,
                    subject,
                    host,
                    port,
                    maskEmail(smtpUsername),
                    maskEmail(effectiveFrom),
                    smtpAuth,
                    startTls,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    attempt);
            scheduleRetryPlain(to, subject, body, attempt);
        } catch (Exception e) {
            log.error(
                    "event=email_send_failed status=FAILED to={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} errorType={} error={} attempt={}",
                    to,
                    subject,
                    host,
                    port,
                    maskEmail(smtpUsername),
                    maskEmail(effectiveFrom),
                    smtpAuth,
                    startTls,
                    e.getClass().getSimpleName(),
                    e.toString(),
                    attempt);
            scheduleRetryPlain(to, subject, body, attempt);
        }
    }

    private void scheduleRetryPlain(String to, String subject, String body, int attempt) {
        if (attempt < MAX_ATTEMPTS) {
            log.info("event=email_retry_scheduled to={} subject={} retryInSeconds={} nextAttempt={}", to, subject, RETRY_DELAY_SECONDS, attempt + 1);
            retryScheduler.schedule(() -> doSendPlain(to, subject, body, attempt + 1), RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
        } else {
            log.error("event=email_send_exhausted status=PERMANENTLY_FAILED to={} subject={} totalAttempts={}", to, subject, attempt);
        }
    }

    /**
     * Send an email with attachments.
     *
     * <p>If the attachments list is null or empty this method delegates to {@link #send(String, String, String)}.
     * The method validates SMTP configuration as in the plain send method and attaches files that exist
     * on the filesystem. Missing files are skipped with a warning. The helper will set the message to
     * multipart so attachments are delivered correctly.
     *
     * <p>If the first attempt fails, a retry is scheduled after 120 seconds.
     *
     * @param to destination email address. If null or blank the send is skipped.
     * @param subject message subject; may be null.
     * @param body message body; may be null.
     * @param attachments list of attachments to include; elements may be null or contain null paths and will be skipped.
     */
    @Override
    public void send(String to, String subject, String body, List<EmailAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            send(to, subject, body);
            return;
        }
        doSendWithAttachments(to, subject, body, attachments, 1);
    }

    private void doSendWithAttachments(String to, String subject, String body, List<EmailAttachment> attachments, int attempt) {
        if (to == null || to.isBlank()) {
            log.info("event=email_send_skipped reason=missing_to attempt={}", attempt);
            return;
        }

        if (host == null || host.isBlank()) {
            log.error("event=email_send_failed reason=smtp_host_missing to={} subject={} attempt={}", to, subject, attempt);
            return;
        }

        if (smtpAuth) {
            if (smtpUsername == null || smtpUsername.isBlank()) {
                log.error("event=email_send_failed reason=smtp_username_missing smtpHost={} smtpPort={} to={} subject={} attempt={}", host, port, to, subject, attempt);
                return;
            }
            if (smtpPassword == null || smtpPassword.isBlank()) {
                log.error("event=email_send_failed reason=smtp_password_missing smtpHost={} smtpPort={} smtpUser={} to={} subject={} attempt={}", host, port, maskEmail(smtpUsername), to, subject, attempt);
                return;
            }
        }

        String effectiveFrom = (configuredFrom != null && !configuredFrom.isBlank()) ? configuredFrom : smtpUsername;
        String bcc = (configuredBcc != null && !configuredBcc.isBlank()) ? configuredBcc.trim() : null;
        String fromName = emailProperties.fromName();

        try {
            var mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());

            if (effectiveFrom != null && !effectiveFrom.isBlank()) {
                if (fromName != null && !fromName.isBlank()) {
                    helper.setFrom(effectiveFrom, fromName);
                } else {
                    helper.setFrom(effectiveFrom);
                }
            }
            helper.setTo(to);
            if (bcc != null) {
                helper.setBcc(bcc);
            }
            String safeSubject = normalizeToAscii(subject == null ? "" : subject);
            helper.setSubject(safeSubject);
            mime.setSubject(safeSubject, StandardCharsets.UTF_8.name());

            String safeBody = normalizeToAscii(body == null ? "" : body);
            helper.setText(safeBody, false);

            int attachedCount = 0;
            for (EmailAttachment att : attachments) {
                if (att == null || att.path() == null) {
                    continue;
                }
                File file = att.path().toFile();
                if (!file.exists() || !file.isFile()) {
                    log.warn("event=email_attachment_skipped reason=file_missing path={}", att.path());
                    continue;
                }
                String filename = (att.filename() == null || att.filename().isBlank()) ? file.getName() : att.filename();
                String contentType = (att.contentType() == null || att.contentType().isBlank()) ? "application/octet-stream" : att.contentType();

                helper.addAttachment(filename, new FileSystemResource(file), contentType);
                attachedCount++;
            }

            mailSender.send(mime);
            log.info(
                    "event=email_sent_with_attachments status=SUCCESS to={} bcc={} subject={} attachmentCount={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} charset={} attempt={}",
                    to,
                    maskEmail(bcc),
                    subject,
                    attachedCount,
                    host,
                    port,
                    maskEmail(smtpUsername),
                    maskEmail(effectiveFrom),
                    smtpAuth,
                    startTls,
                    StandardCharsets.UTF_8.name(),
                    attempt);
        } catch (MailException e) {
            log.error(
                    "event=email_send_failed status=FAILED to={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} errorType={} error={} attempt={}",
                    to,
                    subject,
                    host,
                    port,
                    maskEmail(smtpUsername),
                    maskEmail(effectiveFrom),
                    smtpAuth,
                    startTls,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    attempt);
            scheduleRetryWithAttachments(to, subject, body, attachments, attempt);
        } catch (Exception e) {
            log.error(
                    "event=email_send_failed status=FAILED to={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} errorType={} error={} attempt={}",
                    to,
                    subject,
                    host,
                    port,
                    maskEmail(smtpUsername),
                    maskEmail(effectiveFrom),
                    smtpAuth,
                    startTls,
                    e.getClass().getSimpleName(),
                    e.toString(),
                    attempt);
            scheduleRetryWithAttachments(to, subject, body, attachments, attempt);
        }
    }

    private void scheduleRetryWithAttachments(String to, String subject, String body, List<EmailAttachment> attachments, int attempt) {
        if (attempt < MAX_ATTEMPTS) {
            log.info("event=email_retry_scheduled to={} subject={} retryInSeconds={} nextAttempt={}", to, subject, RETRY_DELAY_SECONDS, attempt + 1);
            retryScheduler.schedule(() -> doSendWithAttachments(to, subject, body, attachments, attempt + 1), RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
        } else {
            log.error("event=email_send_exhausted status=PERMANENTLY_FAILED to={} subject={} totalAttempts={}", to, subject, attempt);
        }
    }

    /**
     * Mask an email address for logging output. The returned string hides part of the local
     * portion for privacy.
     *
     * @param email the email address to mask; may be null.
     * @return masked email or null if input was null/blank.
     */
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

    /**
     * Normalize a string to an ASCII-friendly representation by replacing characters that commonly
     * become '?' when mail systems downgrade charset or perform lossy transforms. This helps
     * preserve readable punctuation such as dashes and smart quotes.
     *
     * @param s input string; may be null.
     * @return normalized string, or the original if null/empty.
     */
    private static String normalizeToAscii(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }

        // Replace characters that commonly become '?' when a hop downgrades encoding.
        return s
                // smart single quotes/apostrophes
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201B', '\'')
                // smart double quotes
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u201E', '"')
                // dashes
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2212', '-')
                // ellipsis
                .replace("\u2026", "...")
                // non-breaking space
                .replace('\u00A0', ' ');
    }
}
