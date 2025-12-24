package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.service.email.EmailSender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple endpoint to send a real test email using the configured SMTP settings.
 *
 * <p>Auth: uses the same X-Admin-Password mechanism as other admin endpoints.
 */
@RestController
@RequestMapping("/api/admin/email")
@RequiredArgsConstructor
@Slf4j
public class EmailTestController {

    private final EmailSender emailSender;

    @Value("${actone.admin.purge-password:}")
    private String purgePassword;

    @PostMapping("/test")
    public ResponseEntity<?> sendTestEmail(
            @RequestHeader(value = "X-Admin-Password", required = false) String headerPassword,
            @Valid @RequestBody TestEmailRequest body
    ) {
        if (purgePassword == null || purgePassword.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Admin endpoints are not enabled"));
        }
        String provided = headerPassword != null && !headerPassword.isBlank() ? headerPassword : body.password();
        if (provided == null || !purgePassword.equals(provided)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Invalid admin password"));
        }

        String subject = body.subject() == null || body.subject().isBlank() ? "Act-One SMTP test" : body.subject();
        String message = body.message() == null || body.message().isBlank()
                ? "This is a test email from Act-One. If you received this, SMTP is working."
                : body.message();

        log.info("event=email_test_request to={} subject={}", body.to(), subject);
        emailSender.send(body.to(), subject, message);

        return ResponseEntity.ok(Map.of(
                "message", "Send attempted (check logs for event=email_sent or event=email_send_failed)",
                "to", body.to(),
                "subject", subject
        ));
    }

    public record TestEmailRequest(
            @NotBlank String password,
            @NotBlank @Email String to,
            String subject,
            String message
    ) {
    }
}

