package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.card.TicketCardGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Generates ticket card JPGs on demand and returns them as a ZIP.
 */
@RestController
@RequestMapping("/api/ticket-cards")
@RequiredArgsConstructor
@Slf4j
public class TicketCardDownloadController {

    private final TicketRepository ticketRepository;
    private final TicketCardGenerator ticketCardGenerator;

    /**
     * Generate ticket images for all tickets belonging to a transactionId and return as a zip file.
     *
     * <p>Response content type: application/zip.
     * <p>Zip entries: jpg
     */
    @GetMapping(value = "/by-transaction/{transactionId}", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> downloadTicketCardsZipByTransactionId(@PathVariable String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String tx = transactionId.trim();
        List<Ticket> tickets = ticketRepository.findByTransactionId(tx);
        if (tickets == null || tickets.isEmpty()) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(outputStream -> outputStream.write(toJsonBytes(Map.of(
                            "message", "Transaction not found",
                            "transactionId", tx
                    ))));
        }

        String zipName = "ticket-cards-transaction-" + tx + ".zip";
        return buildZipResponse(zipName, tickets, "transactionId=" + tx);
    }

    /**
     * Generate ticket images for all tickets belonging to a user and return as a zip file.
     *
     * <p>Response content type: application/zip.
     * <p>Zip entries: jpg
     */
    @GetMapping(value = "/by-user/{userId}", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> downloadTicketCardsZip(@PathVariable String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<Ticket> tickets = ticketRepository.findByUserId(userId.trim());
        if (tickets == null || tickets.isEmpty()) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(outputStream -> {
                        // write small JSON payload
                        outputStream.write(toJsonBytes(Map.of("message", "User not found", "userId", userId)));
                    });
        }

        String zipName = "ticket-cards-user-" + userId.trim() + ".zip";
        return buildZipResponse(zipName, tickets, "userId=" + userId.trim());
    }

    private ResponseEntity<StreamingResponseBody> buildZipResponse(String zipName, List<Ticket> tickets, String logKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(zipName, StandardCharsets.UTF_8).build());

        // Defensive: make iteration deterministic.
        List<Ticket> ordered = tickets.stream()
                .sorted(Comparator.comparing(Ticket::getTicketId))
                .toList();

        StreamingResponseBody stream = out -> {
            try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                for (Ticket t : ordered) {
                    String entryName = "ticket-" + t.getTicketId() + ".jpg";

                    boolean success = false;
                    Exception lastError = null;

                    // Retry up to 4 total attempts (first try + 3 retries)
                    for (int attempt = 1; attempt <= 4; attempt++) {
                        try {
                            byte[] jpg = ticketCardGenerator.generateTicketCardJpegBytes(t);
                            if (jpg == null || jpg.length == 0) {
                                throw new IOException("Generated JPG missing/empty for ticketId=" + t.getTicketId());
                            }

                            zos.putNextEntry(new ZipEntry(entryName));
                            try {
                                zos.write(jpg);
                                success = true;
                                break;
                            } finally {
                                try {
                                    zos.closeEntry();
                                } catch (IOException ignored) {
                                    // ignore
                                }
                            }
                        } catch (Exception e) {
                            lastError = e;
                            log.warn("event=ticket_card_zip_attempt_failed {} ticketId={} attempt={} error={}",
                                    logKey, t.getTicketId(), attempt, e.toString());
                        }

                        // Tiny backoff to avoid hammering CPU in tight loops
                        if (!success && attempt < 4) {
                            try {
                                Thread.sleep(150L * attempt);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }

                    if (!success) {
                        String msg = "Failed to generate ticket card after retries for ticketId=" + t.getTicketId();
                        log.error("event=ticket_card_zip_aborting {} ticketId={} error={}", logKey, t.getTicketId(), String.valueOf(lastError));
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, msg, lastError);
                    }
                }

                zos.finish();
            } catch (IOException e) {
                log.info("Streaming ZIP aborted for {} reason={}", logKey, e.toString());
                throw e;
            }
        };

        return ResponseEntity.ok().headers(headers).body(stream);
    }

    private static byte[] toJsonBytes(Map<String, ?> payload) {
        // Minimal JSON since this endpoint usually returns ZIP. Avoids adding dependencies.
        Map<String, ?> map = payload == null ? Map.of() : payload;
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, ?> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escapeJson(e.getKey())).append('"').append(':');
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"').append(escapeJson(String.valueOf(v))).append('"');
            }
        }
        sb.append('}');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
