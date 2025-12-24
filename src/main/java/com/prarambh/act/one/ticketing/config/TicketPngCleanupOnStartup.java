package com.prarambh.act.one.ticketing.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.Nullable;

/**
 * Deletes previously generated ticket PNGs in the project root on application startup.
 *
 * <p>Only files matching {@code ticket-*.png} in the current working directory are deleted.
 */
@Component
@Slf4j
public class TicketPngCleanupOnStartup implements ApplicationRunner {

    @Override
    @SuppressWarnings({"NullAway"})
    public void run(@Nullable ApplicationArguments args) {
        Path root = Paths.get(".").toAbsolutePath().normalize();

        int deleted = 0;
        int failed = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "ticket-*.png")) {
            for (Path p : stream) {
                try {
                    if (Files.isRegularFile(p)) {
                        Files.deleteIfExists(p);
                        deleted++;
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
