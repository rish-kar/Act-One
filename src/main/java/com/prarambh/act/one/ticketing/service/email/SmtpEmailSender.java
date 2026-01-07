package com.prarambh.act.one.ticketing.service.email;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
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
 * <p>Logging: detailed events are logged for successful sends and failures. Sensitive values such as
 * SMTP username and addresses are masked in logs.
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

      @Value("${actone.email.bcc:}")
      private String configuredBcc;

      @Value("${actone.email.from-name:}")
      private String configuredFromName;

      /**
       * Send a plain-text email to a single recipient.
       *
       * <p>The method validates that a recipient is provided and that SMTP host and authentication
       * details (when required) are available. It sets the message subject and body ensuring UTF-8
       * charset is used. If a configured BCC exists it will be applied. Errors are logged and do not
       * throw exceptions to callers (send failures are recorded in logs).
       *
       * @param to destination email address. If null or blank the send is skipped.
       * @param subject message subject; may be null.
       * @param body plain-text message body; may be null.
       */
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

            // Retry logic for transient SSL/TLS failures
            int maxAttempts = 3;
            Exception lastException = null;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                  try {
                        var mime = mailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());

                        if (effectiveFrom != null && !effectiveFrom.isBlank()) {
                              String fromName = (configuredFromName != null && !configuredFromName.isBlank())
                                      ? configuredFromName.trim()
                                      : null;

                              if (fromName != null) {
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
                                "event=email_sent to={} bcc={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} charset={} attempt={}",
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
                        return; // Success, exit retry loop
                  } catch (MailException e) {
                        lastException = e;
                        String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                        boolean isRetryable = errorMsg.contains("SSLHandshakeException")
                                || errorMsg.contains("Could not convert socket to TLS")
                                || errorMsg.contains("Remote host terminated the handshake")
                                || errorMsg.contains("Connection reset");

                        if (isRetryable && attempt < maxAttempts) {
                              long delayMs = 1000L * attempt * attempt; // Exponential backoff: 1s, 4s, 9s
                              log.warn("event=email_send_retry to={} subject={} attempt={} maxAttempts={} delayMs={} error={}",
                                      to, subject, attempt, maxAttempts, delayMs, e.getMessage());
                              try {
                                    Thread.sleep(delayMs);
                              } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                              }
                        } else {
                              log.error(
                                      "event=email_send_failed to={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} errorType={} error={} attempt={}",
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
                              return;
                        }
                  } catch (Exception e) {
                        log.error(
                                "event=email_send_failed to={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} errorType={} error={} attempt={}",
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
                        return; // Non-retryable exception
                  }
            }

            // All retries exhausted
            if (lastException != null) {
                  log.error("event=email_send_failed_all_retries to={} subject={} maxAttempts={} lastError={}",
                          to, subject, maxAttempts, lastException.getMessage());
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

            // Retry logic for transient SSL/TLS failures
            int maxAttempts = 3;
            Exception lastException = null;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                  try {
                        var mime = mailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());

                        if (effectiveFrom != null && !effectiveFrom.isBlank()) {
                              String fromName = (configuredFromName != null && !configuredFromName.isBlank())
                                      ? configuredFromName.trim()
                                      : null;

                              if (fromName != null) {
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
                        long totalAttachmentSize = 0;
                        for (EmailAttachment att : attachments) {
                              if (att == null) {
                                    continue;
                              }

                              String filename = (att.filename() == null || att.filename().isBlank()) ? "attachment" : att.filename();
                              final String filenameFinal = filename;
                              String contentType = (att.contentType() == null || att.contentType().isBlank()) ? "application/octet-stream" : att.contentType();

                              if (att.bytes() != null) {
                                    byte[] bytes = att.bytes();
                                    helper.addAttachment(filenameFinal, new ByteArrayResource(bytes) {
                                          @Override
                                          public String getFilename() {
                                                return filenameFinal;
                                          }
                                    }, contentType);
                                    attachedCount++;
                                    totalAttachmentSize += bytes.length;
                                    continue;
                              }

                              if (att.path() == null) {
                                    continue;
                              }

                              File file = att.path().toFile();
                              if (!file.exists() || !file.isFile()) {
                                    log.warn("event=email_attachment_skipped reason=file_missing path={}", att.path());
                                    continue;
                              }

                              if (att.filename() == null || att.filename().isBlank()) {
                                    filename = file.getName();
                              }

                              helper.addAttachment(filename, new FileSystemResource(file), contentType);
                              attachedCount++;
                              totalAttachmentSize += file.length();
                        }

                        log.info("event=email_send_starting to={} subject={} attachmentCount={} totalAttachmentSizeBytes={} attempt={}", to, subject, attachedCount, totalAttachmentSize, attempt);
                        mailSender.send(mime);
                        log.info(
                                "event=email_sent_with_attachments to={} bcc={} subject={} attachmentCount={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} charset={} attempt={}",
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
                        return; // Success, exit retry loop
                  } catch (MailException e) {
                        lastException = e;
                        String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                        boolean isRetryable = errorMsg.contains("SSLHandshakeException")
                                || errorMsg.contains("Could not convert socket to TLS")
                                || errorMsg.contains("Remote host terminated the handshake")
                                || errorMsg.contains("Connection reset");

                        if (isRetryable && attempt < maxAttempts) {
                              long delayMs = 1000L * attempt * attempt; // Exponential backoff: 1s, 4s, 9s
                              log.warn("event=email_send_retry to={} subject={} attempt={} maxAttempts={} delayMs={} error={}",
                                      to, subject, attempt, maxAttempts, delayMs, e.getMessage());
                              try {
                                    Thread.sleep(delayMs);
                              } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                              }
                        } else {
                              log.error(
                                      "event=email_send_failed to={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} errorType={} error={} attempt={}",
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
                              return;
                        }
                  } catch (Exception e) {
                        log.error(
                                "event=email_send_failed to={} subject={} smtpHost={} smtpPort={} smtpUser={} from={} authEnabled={} startTlsEnabled={} errorType={} error={} attempt={}",
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
                        return; // Non-retryable exception
                  }
            }

            // All retries exhausted
            if (lastException != null) {
                  log.error("event=email_send_failed_all_retries to={} subject={} maxAttempts={} lastError={}",
                          to, subject, maxAttempts, lastException.getMessage());
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