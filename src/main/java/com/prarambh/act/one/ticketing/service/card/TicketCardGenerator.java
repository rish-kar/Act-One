package com.prarambh.act.one.ticketing.service.card;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.prarambh.act.one.ticketing.model.Ticket;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * Generates a "ticket card" PNG by drawing a scannable 1D barcode containing {@code barcodeId}
 * onto a template image.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketCardGenerator {

    // Template image dimensions: 688 x 1600.
    // White box: top-left (231, 996), bottom-right (448, 1061).
    private static final int BOX_X1 = 231;
    private static final int BOX_Y1 = 996;
    private static final int BOX_X2 = 448;
    private static final int BOX_Y2 = 1061;

    private static final int BOX_W = BOX_X2 - BOX_X1;
    private static final int BOX_H = BOX_Y2 - BOX_Y1;

    // TicketId text box bounds (template is 688x1600; coordinates provided were for a different template).
    // Scale given (880x2048) coordinates to current template size.
    // x scale: 688/880, y scale: 1600/2048
    private static final int TICKET_ID_X1 = (int) Math.round(277 * (688.0 / 880.0));
    private static final int TICKET_ID_Y1 = (int) Math.round(1395 * (1600.0 / 2048.0));
    private static final int TICKET_ID_X2 = (int) Math.round(590 * (688.0 / 880.0));
    private static final int TICKET_ID_Y2 = (int) Math.round(1480 * (1600.0 / 2048.0));
    private static final int TICKET_ID_W = TICKET_ID_X2 - TICKET_ID_X1;
    private static final int TICKET_ID_H = TICKET_ID_Y2 - TICKET_ID_Y1;

    private final ResourceLoader resourceLoader;
    private final TicketCardProperties properties;

    public Path generateTicketCardPng(Ticket ticket) {
        if (!properties.enabled()) {
            log.debug("event=ticket_card_generation_skipped reason=disabled ticketId={} barcodeId={}", ticket.getTicketId(), ticket.getBarcodeId());
            return null;
        }

        if (ticket.getBarcodeId() == null || ticket.getBarcodeId().isBlank()) {
            throw new IllegalArgumentException("barcodeId must be present");
        }
        if (ticket.getTicketId() == null) {
            throw new IllegalArgumentException("ticketId must be present");
        }

        BufferedImage template = loadTemplate();

        // Keep the encoded barcode payload compact for reliable scanning.
        String encoded = normalizeBarcodeContents(ticket.getBarcodeId());

        // Generate a Code 128 barcode EXACTLY the size of the target box.
        BufferedImage barcode = generateCode128(encoded, BOX_W, BOX_H);

        BufferedImage out = new BufferedImage(template.getWidth(), template.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            g.drawImage(template, 0, 0, null);

            // Ensure the barcode is placed inside an all-white region.
            g.setColor(Color.WHITE);
            g.fillRect(BOX_X1, BOX_Y1, BOX_W, BOX_H);

            // Hard guarantee: nothing can render outside the barcode box.
            java.awt.Shape oldClip = g.getClip();
            g.setClip(new java.awt.Rectangle(BOX_X1, BOX_Y1, BOX_W, BOX_H));
            try {
                // Corner-to-corner inside the exact coordinates.
                g.drawImage(barcode, BOX_X1, BOX_Y1, null);
            } finally {
                g.setClip(oldClip);
            }

            // Draw ticketId text below barcode (centered in bounding box)
            drawTicketId(g, ticket.getTicketId().toString());
        } finally {
            g.dispose();
        }

        Path outputDir = resolveOutputDir();
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create output directory: " + outputDir, e);
        }

        Path output = outputDir.resolve("ticket-" + ticket.getTicketId() + ".png");
        try {
            javax.imageio.ImageIO.write(out, "png", output.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write ticket card PNG to " + output, e);
        }

        log.info("event=ticket_card_generated path={} ticketId={} barcodeId={}", output.toAbsolutePath(), ticket.getTicketId(), ticket.getBarcodeId());
        return output;
    }

    private BufferedImage loadTemplate() {
        String resourcePath = properties.templateResource() == null || properties.templateResource().isBlank()
                ? "classpath:/static/Card.jpeg"
                : properties.templateResource();

        Resource resource = resourceLoader.getResource(resourcePath);
        try (InputStream is = resource.getInputStream()) {
            BufferedImage img = javax.imageio.ImageIO.read(is);
            if (img == null) {
                throw new IllegalStateException("Template image could not be decoded: " + resourcePath);
            }
            return img;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template image: " + resourcePath, e);
        }
    }

    private static String normalizeBarcodeContents(String barcodeId) {
        if (barcodeId == null) {
            return "";
        }
        // The barcode printed must match the database value exactly.
        return barcodeId.trim();
    }

    private static BufferedImage generateCode128(String contents, int width, int height) {
        // Code 128 supports full ASCII subset and is robust for short IDs.
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        // Add quiet zone inside the generated barcode bitmap for scanner reliability.
        // This does not change the placement box, it only reserves white space within it.
        hints.put(EncodeHintType.MARGIN, 10);

        BitMatrix matrix;
        try {
            matrix = new MultiFormatWriter().encode(contents, BarcodeFormat.CODE_128, width, height, hints);
        } catch (WriterException e) {
            throw new IllegalStateException("Failed to encode barcode", e);
        }

        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private static void drawTicketId(Graphics2D g, String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            return;
        }

        // Use anti-aliasing for text only.
        Object oldTextAa = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        Object oldAa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        try {
            // Two-line label within the same bounding box.
            String line1 = "Ticket Number:";
            String line2 = ticketId;

            FontRenderContext frc = g.getFontRenderContext();

            // Choose font sizes that fit the box (simple conservative defaults).
            Font font1 = new Font("SansSerif", Font.BOLD, 22);
            Font font2 = new Font("SansSerif", Font.BOLD, 22);

            TextLayout layout1 = new TextLayout(line1, font1, frc);
            TextLayout layout2 = new TextLayout(line2, font2, frc);

            double w1 = layout1.getBounds().getWidth();
            double h1 = layout1.getBounds().getHeight();
            double w2 = layout2.getBounds().getWidth();
            double h2 = layout2.getBounds().getHeight();

            // If the UUID line doesn't fit, shrink it a bit (down to 16).
            for (int size = 22; size >= 16 && w2 > (TICKET_ID_W - 6); size--) {
                font2 = new Font("SansSerif", Font.BOLD, size);
                layout2 = new TextLayout(line2, font2, frc);
                w2 = layout2.getBounds().getWidth();
                h2 = layout2.getBounds().getHeight();
            }
            double lineGap = 9;
            double moveUpPx = 2;
            double totalH = h1 + lineGap + h2;
            double startY = TICKET_ID_Y1 + (TICKET_ID_H - totalH) / 2.0 - moveUpPx;

            float x1 = (float) (TICKET_ID_X1 + (TICKET_ID_W - w1) / 2.0);
            float y1 = (float) (startY - layout1.getBounds().getY());

            float x2 = (float) (TICKET_ID_X1 + (TICKET_ID_W - w2) / 2.0);
            float y2 = (float) (startY + h1 + lineGap - layout2.getBounds().getY());

            drawOutlinedText(g, layout1, x1, y1);
            drawOutlinedText(g, layout2, x2, y2);
        } finally {
            if (oldTextAa != null) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldTextAa);
            }
            if (oldAa != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
            }
        }
    }

    private static void drawOutlinedText(Graphics2D g, TextLayout layout, float x, float y) {
        Shape outline = layout.getOutline(java.awt.geom.AffineTransform.getTranslateInstance(x, y));

        // Dark stroke outline for contrast.
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0, 0, 0, 200));
        g.draw(outline);

        // White fill.
        g.setColor(new Color(255, 255, 255, 245));
        g.fill(outline);
    }

    private Path resolveOutputDir() {
        String configured = properties.outputDir();
        if (configured == null || configured.isBlank()) {
            return Paths.get(".").toAbsolutePath().normalize();
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }
}
