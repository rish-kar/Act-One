package com.prarambh.act.one.ticketing.service.email;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends emails via SMTP using Spring's JavaMailSender.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailSender implements EmailSender {

    private final org.springframework.mail.javamail.JavaMailSender mailSender;
    private final EmailProperties emailProperties;

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

    @Override
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.info("event=email_send_skipped reason=missing_to");
            return;
        }

        if (host == null || host.isBlank()) {
            log.error("event=email_send_failed reason=smtp_host_missing to={} subject={}", to, subject);
            return;
        }

        if (smtpAuth) {
            if (smtpUsername == null || smtpUsername.isBlank()) {
                log.error("event=email_send_failed reason=smtp_username_missing smtpHost={} smtpPort={} to={} subject={}", host, port, to, subject);
                return;
            }
            if (smtpPassword == null || smtpPassword.isBlank()) {
                log.error("event=email_send_failed reason=smtp_password_missing smtpHost={} smtpPort={} smtpUser={} to={} subject={}", host, port, maskEmail(smtpUsername), to, subject);
                return;
            }
        }

        String effectiveFrom = (configuredFrom != null && !configuredFrom.isBlank()) ? configuredFrom : smtpUsername;
        String bcc = (configuredBcc != null && !configuredBcc.isBlank()) ? configuredBcc.trim() : null;

        try {
            var mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());

            if (effectiveFrom != null && !effectiveFrom.isBlank()) {
                helper.setFrom(effectiveFrom);
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
                    "event=email_sent to={} bcc={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} charset={}",
                    to,
                    maskEmail(bcc),
                    subject,
                    host,
                    port,
                    maskEmail(smtpUsername),
                    maskEmail(effectiveFrom),
                    smtpAuth,
                    startTls,
                    StandardCharsets.UTF_8.name());
        } catch (MailException e) {
            log.error(
                    "event=email_send_failed to={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} errorType={} error={}",
                    to,
                    subject,
                    host,
                    port,
                    maskEmail(smtpUsername),
                    maskEmail(effectiveFrom),
                    smtpAuth,
                    startTls,
                    e.getClass().getSimpleName(),
                    e.getMessage());
        } catch (Exception e) {
            log.error(
                    "event=email_send_failed to={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} errorType={} error={}",
                    to,
                    subject,
                    host,
                    port,
                    maskEmail(smtpUsername),
                    maskEmail(effectiveFrom),
                    smtpAuth,
                    startTls,
                    e.getClass().getSimpleName(),
                    e.toString());
        }
    }

    @Override
    public void send(String to, String subject, String body, List<EmailAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            send(to, subject, body);
            return;
        }

        if (to == null || to.isBlank()) {
            log.info("event=email_send_skipped reason=missing_to");
            return;
        }

        if (host == null || host.isBlank()) {
            log.error("event=email_send_failed reason=smtp_host_missing to={} subject={}", to, subject);
            return;
        }

        if (smtpAuth) {
            if (smtpUsername == null || smtpUsername.isBlank()) {
                log.error("event=email_send_failed reason=smtp_username_missing smtpHost={} smtpPort={} to={} subject={}", host, port, to, subject);
                return;
            }
            if (smtpPassword == null || smtpPassword.isBlank()) {
                log.error("event=email_send_failed reason=smtp_password_missing smtpHost={} smtpPort={} smtpUser={} to={} subject={}", host, port, maskEmail(smtpUsername), to, subject);
                return;
            }
        }

        String effectiveFrom = (configuredFrom != null && !configuredFrom.isBlank()) ? configuredFrom : smtpUsername;
        String bcc = (configuredBcc != null && !configuredBcc.isBlank()) ? configuredBcc.trim() : null;

        try {
            var mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());

            if (effectiveFrom != null && !effectiveFrom.isBlank()) {
                helper.setFrom(effectiveFrom);
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
                    "event=email_sent_with_attachments to={} bcc={} subject={} attachmentCount={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} charset={}",
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
                    StandardCharsets.UTF_8.name());
        } catch (MailException e) {
            log.error(
                    "event=email_send_failed to={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} errorType={} error={}",
                    to,
                    subject,
                    host,
                    port,
                    maskEmail(smtpUsername),
                    maskEmail(effectiveFrom),
                    smtpAuth,
                    startTls,
                    e.getClass().getSimpleName(),
                    e.getMessage());
        } catch (Exception e) {
            log.error(
                    "event=email_send_failed to={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} errorType={} error={}",
                    to,
                    subject,
                    host,
                    port,
                    maskEmail(smtpUsername),
                    maskEmail(effectiveFrom),
                    smtpAuth,
                    startTls,
                    e.getClass().getSimpleName(),
                    e.toString());
        }
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
