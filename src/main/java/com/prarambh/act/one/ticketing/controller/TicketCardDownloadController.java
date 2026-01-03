package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.card.TicketCardGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Generates ticket card PNGs on demand and returns them as a ZIP.
 */
@RestController
@RequestMapping("/api/ticket-cards")
@RequiredArgsConstructor
@Slf4j
public class TicketCardDownloadController {

    private final TicketRepository ticketRepository;
    private final TicketCardGenerator ticketCardGenerator;

    /**
     * Generate ticket images for all tickets belonging to a user and return as a zip file.
     *
     * <p>Response content type: application/zip.
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(zipName, StandardCharsets.UTF_8).build());
        // Do not set content length for streaming responses

        StreamingResponseBody stream = out -> {
            try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                for (Ticket t : tickets) {
                    try {
                        Path png = ticketCardGenerator.generateTicketCardPng(t);
                        if (png == null || !Files.exists(png)) {
                            continue;
                        }

                        String entryName = "ticket-" + t.getTicketId() + ".png";
                        zos.putNextEntry(new ZipEntry(entryName));
                        // stream file into zip entry
                        try {
                            Files.copy(png, zos);
                        } catch (IOException ioe) {
                            log.warn("Failed to write ticket image for {} into zip: {}", t.getTicketId(), ioe.toString());
                            // If client aborted, break out
                            break;
                        }
                        zos.closeEntry();
                        // flush the zip stream to the client periodically
                        zos.flush();
                    } catch (Exception e) {
                        log.warn("Skipping ticket {} due to error: {}", t.getTicketId(), e.toString());
                    }
                }
                // Ensure zip stream is finished
                zos.finish();
            } catch (IOException e) {
                // Client aborted or IO error while streaming; just log and return
                log.info("Streaming ZIP aborted for userId={} reason={}", userId, e.toString());
            }
        };

        return ResponseEntity.ok()
                .headers(headers)
                .body(stream);
    }

    // Compatibility alias: some clients still call 'by-customer' paths. Delegate to the same logic.
    @GetMapping(value = "/by-customer/{customerId}", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> downloadTicketCardsZipByCustomer(@PathVariable String customerId) {
        return downloadTicketCardsZip(customerId);
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
