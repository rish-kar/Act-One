package com.prarambh.act.one.ticketing.controller;

import com.prarambh.act.one.ticketing.model.Ticket;
import com.prarambh.act.one.ticketing.repository.TicketRepository;
import com.prarambh.act.one.ticketing.service.card.TicketCardGenerator;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
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
                        Path png = null;
                        try {
                            png = ticketCardGenerator.generateTicketCardPng(t);
                            if (png == null || !Files.exists(png)) {
                                throw new IOException("Generated PNG missing for ticketId=" + t.getTicketId());
                            }

                            zos.putNextEntry(new ZipEntry(entryName));
                            try {
                                writeJpgFromPngPath(png, zos);
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

                            // Best-effort cleanup between attempts
                            // (Generator writes temp PNGs; remove them to prevent buildup)
                        } finally {
                            if (png != null) {
                                try {
                                    Files.deleteIfExists(png);
                                } catch (Exception ignored) {
                                    // ignore
                                }
                            }
                        }

                        // Tiny backoff to avoid hammering disk/imageio in tight loops
                        if (!success && attempt < 4) {
                            try {
                                Thread.sleep(150L * attempt);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                // If interrupted, abort because we can't guarantee completion.
                                break;
                            }
                        }
                    }

                    if (!success) {
                        // Do NOT return partial ZIPs.
                        String msg = "Failed to generate ticket card after retries for ticketId=" + t.getTicketId();
                        log.error("event=ticket_card_zip_aborting {} ticketId={} error={}", logKey, t.getTicketId(), String.valueOf(lastError));
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, msg, lastError);
                    }
                }

                zos.finish();
            } catch (IOException e) {
                // Client aborted or IO error while streaming
                log.info("Streaming ZIP aborted for {} reason={}", logKey, e.toString());
                throw e;
            }
        };

        return ResponseEntity.ok().headers(headers).body(stream);
    }

    /**
     * Converts a PNG file on disk to a standards-compliant baseline JPEG stream.
     *
     * <p>Using an explicit ImageWriter avoids some environments producing slightly odd JPEGs.
     */
    private static void writeJpgFromPngPath(Path png, OutputStream out) throws IOException {
        BufferedImage img;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(png))) {
            img = ImageIO.read(in);
        }
        if (img == null) {
            throw new IOException("ImageIO could not decode PNG: " + png);
        }

        // JPG doesn’t support alpha; flatten onto white background.
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(img, 0, 0, null);
        } finally {
            g.dispose();
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            writers = ImageIO.getImageWritersByFormatName("jpg");
        }
        if (!writers.hasNext()) {
            throw new IOException("No ImageIO writer available for jpeg/jpg");
        }

        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);

            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                // High quality but smaller than PNG; tweak if needed.
                param.setCompressionQuality(0.9f);
            }

            writer.write(null, new IIOImage(rgb, null, null), param);
            ios.flush();
        } finally {
            writer.dispose();
        }
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
