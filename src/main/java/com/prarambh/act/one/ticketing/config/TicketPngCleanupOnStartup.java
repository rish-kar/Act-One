package com.prarambh.act.one.ticketing.config;

import com.prarambh.act.one.ticketing.service.card.TicketCardProperties;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Deletes previously generated ticket PNGs in the configured output directory on application startup.
 *
 * <p>The directory used is {@link TicketCardProperties#outputDir()}. If not configured the current
 * working directory is used. Only files matching {@code ticket-*.png} are removed. Failures are
 * logged but do not prevent the application from starting.
 */
@Component
@Slf4j
public class TicketPngCleanupOnStartup implements ApplicationRunner {

    private final TicketCardProperties ticketCardProperties;

    public TicketPngCleanupOnStartup(TicketCardProperties ticketCardProperties) {
        this.ticketCardProperties = ticketCardProperties;
    }

    /**
     * Run the cleanup at startup. This method will iterate the configured output directory and
     * delete files matching the glob {@code ticket-*.png}.
     *
     * @param args application arguments (ignored)
     */
    @Override
    public void run(ApplicationArguments args) {
        // Resolve the same output directory used by TicketCardGenerator.
        String configured = ticketCardProperties.outputDir();
        Path root = (configured == null || configured.isBlank())
                ? Paths.get(".").toAbsolutePath().normalize()
                : Paths.get(configured).toAbsolutePath().normalize();

        int deleted = 0;
        int failed = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "ticket-*.png")) {
            for (Path p : stream) {
                try {
                    if (Files.isRegularFile(p)) {
                        boolean removed = Files.deleteIfExists(p);
                        if (removed) {
                            deleted++;
                        }
                    }
                } catch (IOException e) {
                    failed++;
                    log.warn("event=ticket_png_cleanup_failed path={} error={}", p.toAbsolutePath(), e.toString());
                }
            }
        } catch (IOException e) {
            log.warn("event=ticket_png_cleanup_failed reason=list_failed root={} error={}", root, e.toString());
            return;
        }

        if (deleted > 0 || failed > 0) {
            log.info("event=ticket_png_cleanup_complete root={} deletedCount={} failedCount={}", root, deleted, failed);
        }
    }
}
