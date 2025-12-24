package com.prarambh.act.one.ticketing.service.email;

import java.io.File;
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

        try {
            org.springframework.mail.SimpleMailMessage msg = new org.springframework.mail.SimpleMailMessage();
            if (effectiveFrom != null && !effectiveFrom.isBlank()) {
                msg.setFrom(effectiveFrom);
            }
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);

            mailSender.send(msg);
            log.info(
                    "event=email_sent to={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={}",
                    to,
                    subject,
                    host,
                    port,
                    maskEmail(smtpUsername),
                    maskEmail(effectiveFrom),
                    smtpAuth,
                    startTls);
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

        try {
            var mime = mailSender.createMimeMessage();
            // true => multipart
            MimeMessageHelper helper = new MimeMessageHelper(mime, true);

            if (effectiveFrom != null && !effectiveFrom.isBlank()) {
                helper.setFrom(effectiveFrom);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);

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
                    "event=email_sent_with_attachments to={} subject={} attachmentCount={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={}",
                    to,
                    subject,
                    attachedCount,
                    host,
                    port,
                    maskEmail(smtpUsername),
                    maskEmail(effectiveFrom),
                    smtpAuth,
                    startTls);
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
}
