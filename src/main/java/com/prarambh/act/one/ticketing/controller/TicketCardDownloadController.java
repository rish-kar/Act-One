package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.card.TicketCardGenerator;
import java.io.ByteArrayOutputStream;
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
    public ResponseEntity<byte[]> downloadTicketCardsZip(@PathVariable String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(new byte[0]);
        }

        List<Ticket> tickets = ticketRepository.findByUserId(userId.trim());
        if (tickets == null || tickets.isEmpty()) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toJsonBytes(Map.of("message", "User not found", "userId", userId)));
        }

        String zipName = "ticket-cards-user-" + userId.trim() + ".zip";

        try {
            byte[] zipBytes = buildZip(tickets);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/zip"));
            headers.setContentDisposition(ContentDisposition.attachment().filename(zipName, StandardCharsets.UTF_8).build());
            headers.setContentLength(zipBytes.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(zipBytes);
        } catch (Exception e) {
            log.error("event=ticket_cards_zip_failed userId={} error={}", userId, e.toString());
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toJsonBytes(Map.of("message", "Failed to generate ticket cards", "userId", userId)));
        }
    }

    private byte[] buildZip(List<Ticket> tickets) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (Ticket t : tickets) {
                Path png = ticketCardGenerator.generateTicketCardPng(t);
                if (png == null) {
                    continue;
                }

                String entryName = "ticket-" + t.getTicketId() + ".png";
                zos.putNextEntry(new ZipEntry(entryName));
                Files.copy(png, zos);
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
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
