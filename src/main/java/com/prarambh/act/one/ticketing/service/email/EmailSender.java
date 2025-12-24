package com.prarambh.act.one.ticketing.service.email;

/** Contract for sending emails (SMTP, test double, or future provider integration). */
public interface EmailSender {

    void send(String to, String subject, String body);
}

