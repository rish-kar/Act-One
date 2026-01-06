package com.prarambh.act.one.ticketing.service.email;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Probes basic TCP connectivity to the configured SMTP host/port on startup.
 */
@Component
@Slf4j
public class SmtpConnectivityDiagnostics implements ApplicationRunner {

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${spring.mail.port:0}")
    private int port;

    @Value("${actone.email.enabled:true}")
    private boolean emailEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!emailEnabled) {
            log.info("event=smtp_connectivity_skipped reason=email_disabled");
            return;
        }
        if (host == null || host.isBlank() || port <= 0) {
            log.warn("event=smtp_connectivity_skipped reason=missing_host_or_port host={} port={}", host, port);
            return;
        }

        try (Socket socket = new Socket()) {
            long start = System.nanoTime();
            socket.connect(new InetSocketAddress(host, port), 10000);
            long tookMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            log.info("event=smtp_connectivity_ok host={} port={} tookMs={}", host, port, tookMs);
        } catch (Exception e) {
            log.error("event=smtp_connectivity_failed host={} port={} error={}", host, port, e.getMessage());
        }
    }
}
