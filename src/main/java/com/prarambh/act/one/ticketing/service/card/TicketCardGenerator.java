package com.prarambh.act.one.ticketing.service.card;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.prarambh.act.one.ticketing.model.Ticket;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * Generates a "ticket card" PNG by drawing a scannable code containing {@code qrCodeId}
 * (linear code plus QR where configured) and placing it onto a template image.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketCardGenerator {

    // NOTE: Card.png is the large poster template (coordinates below are based on that).
    // We do NOT render the legacy small 1D barcode box anymore.

    // (Legacy coordinate constants removed; we compute placement dynamically now.)

    // --- Layout tuning constants ---
    // Base QR box size (we render QR smaller than the available area per request).
    private static final int QR_BOX_W = 600;
    private static final int QR_BOX_H = 460;
    // Reduce QR size: render inside the box at this factor.
     // Increased by 30% from 0.75 -> 0.975 (position unchanged)
    private static final double QR_SCALE_FACTOR = 0.975;
    // Move QR up by 2% of QR box height.
    private static final double QR_Y_UP_FACTOR = 0.07;
    // Move QR right by 1% of template width.
    private static final double QR_X_RIGHT_FACTOR = 0.01;

    // Ticket label box size (position computed dynamically from template width).
    // Increase ticket box width so a larger font can be used without shrinking.
    private static final int TICKET_ID_W = 760;
    private static final int TICKET_ID_H = 220;
    // Shift ticket number 5% right relative to centered X.
    // Shift ticket number horizontally: negative = left, positive = right.
    // Moved 20% towards left as requested.
    private static final double TICKET_RIGHT_SHIFT_FACTOR = 0;
    // Increase ticket fonts.
    // Increased by 10% as requested
    private static final float TICKET_LABEL_FONT_PX = 42.0f;
    // Increased ticket number (value) size as requested — raised to 90px
    private static final float TICKET_VALUE_FONT_PX = 72.0f;
    // Enforce a minimum font so the value remains large even for long IDs.
    private static final int TICKET_VALUE_MIN_FONT_PX = 36;

    // Y anchors (keep current vertical region; adjust only QR by -2% per request).
    private static final int QR_Y_BASE = 1904;
    // Restore ticket number to last working position (the prior spec block).
    private static final int TICKET_ID_Y1 = 2480;

    private final ResourceLoader resourceLoader;
    private final TicketCardProperties properties;

    public Path generateTicketCardPng(Ticket ticket) {
        if (!properties.enabled()) {
            log.debug("event=ticket_card_generation_skipped reason=disabled ticketId={} qrCodeId={}", ticket.getTicketId(), ticket.getQrCodeId());
            return null;
        }

        if (ticket.getQrCodeId() == null || ticket.getQrCodeId().isBlank()) {
            throw new IllegalArgumentException("qrCodeId must be present");
        }
        if (ticket.getTicketId() == null) {
            throw new IllegalArgumentException("ticketId must be present");
        }

        BufferedImage template = loadTemplate();

        // Compute dynamic boxes from the real template dimensions.
        int templateW = template.getWidth();
        int templateH = template.getHeight();

        // QR: center on X axis, reduce size, shift up by 2% (of base QR box height), and right by 1% (of template width).
        int qrRenderW = (int) Math.round(QR_BOX_W * QR_SCALE_FACTOR);
        int qrRenderH = (int) Math.round(QR_BOX_H * QR_SCALE_FACTOR);
        int qrX = (templateW - qrRenderW) / 2 + (int) Math.round(templateW * QR_X_RIGHT_FACTOR);
        int qrY = (int) Math.round(QR_Y_BASE - (QR_BOX_H * QR_Y_UP_FACTOR));

        // Generate QR for the QR area using qrCodeId as the payload.
        BufferedImage qr = renderQrCode(ticket.getQrCodeId(), qrRenderW, qrRenderH);

        BufferedImage out = new BufferedImage(template.getWidth(), template.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            // No interpolation for QR/linear code placement.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

            g.drawImage(template, 0, 0, null);

            // Draw QR in its dedicated area (no extra white rectangle outside the QR code itself).
            // The QR image is "white modules on transparent background", so the template shows through.
            g.drawImage(qr, qrX, qrY, null);

            // Ticket number label in new position.
            drawTicketId(g, ticket.getTicketId().toString(), templateW, templateH);
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

        log.info("event=ticket_card_generated path={} ticketId={} qrCodeId={}", output.toAbsolutePath(), ticket.getTicketId(), ticket.getQrCodeId());
        return output;
    }

    private BufferedImage loadTemplate() {
        String resourcePath = properties.templateResource() == null || properties.templateResource().isBlank()
                ? "classpath:/static/Card.png"
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

    /**
     * Renders a machine-readable Code 128 symbol with controlled quiet zones.
     *
     * <p>Critical properties:
     * <ul>
     *   <li>Natural-width encoding (no forced stretching)</li>
     *   <li>Integer-only scaling (preserves bar/space ratios)</li>
     *   <li>Clean white canvas (quiet zone not polluted by borders)</li>
     * </ul>
     */
    private static BufferedImage renderCode128(String contents, int targetW, int targetH, int quiet, int minScale, int maxScale) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 0); // we handle quiet zones ourselves

        BitMatrix bm;
        try {
            bm = new Code128Writer().encode(contents, BarcodeFormat.CODE_128, 1, 60, hints);
        } catch (RuntimeException e) {
            // ZXing may throw unchecked exceptions for invalid inputs; wrap for clarity.
            throw new IllegalStateException("Failed to encode code symbol", e);
        }

        int mw = bm.getWidth();
        int mh = bm.getHeight();

        // Determine integer scale that fits inside target with explicit left/right quiet zones.
        int maxScaleX = (targetW - (2 * quiet)) / mw;
        int maxScaleY = targetH / mh;
        int scale = Math.min(maxScaleX, maxScaleY);

        if (scale < minScale) {
            scale = minScale;
        }
        if (maxScale > 0) {
            scale = Math.min(scale, maxScale);
        }

        int renderW = mw * scale;
        int renderH = mh * scale;

        int x0 = (targetW - renderW) / 2;
        int y0 = (targetH - renderH) / 2;

        // Clean white canvas (TYPE_INT_RGB => no alpha blending / no partial pixels)
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetW, targetH);

            g.setColor(Color.BLACK);
            for (int y = 0; y < mh; y++) {
                for (int x = 0; x < mw; x++) {
                    if (bm.get(x, y)) {
                        g.fillRect(x0 + (x * scale), y0 + (y * scale), scale, scale);
                    }
                }
            }
        } finally {
            g.dispose();
        }

        return out;
    }

    private static BufferedImage renderQrCode(String contents, int targetW, int targetH) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        // Use at least 1 module quiet zone. The QR is placed on a template, so keep it compact.
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix bm = encodeQrMatrix(contents, hints);
        int mw = bm.getWidth();
        int mh = bm.getHeight();

        int maxScaleX = targetW / mw;
        int maxScaleY = targetH / mh;
        // Enforce a minimum integer scale so modules don't collapse into noise.
        int scale = Math.max(2, Math.min(maxScaleX, maxScaleY));

        int renderW = mw * scale;
        int renderH = mh * scale;

        int x0 = (targetW - renderW) / 2;
        int y0 = (targetH - renderH) / 2;

        // White QR modules on transparent background.
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(0, 0, targetW, targetH);
            g.setComposite(java.awt.AlphaComposite.Src);

            g.setColor(Color.WHITE);
            for (int y = 0; y < mh; y++) {
                for (int x = 0; x < mw; x++) {
                    if (bm.get(x, y)) {
                        g.fillRect(x0 + x * scale, y0 + y * scale, scale, scale);
                    }
                }
            }
        } finally {
            g.dispose();
        }

        return out;
    }

    private static BitMatrix encodeQrMatrix(String contents, Map<EncodeHintType, Object> hints) {
        try {
            // Non-zero dimensions avoid ZXing edge cases. We scale ourselves later.
            return new MultiFormatWriter().encode(contents, BarcodeFormat.QR_CODE, 120, 120, hints);
        } catch (WriterException e) {
            throw new IllegalStateException("Failed to encode QR", e);
        }
    }

    private static void drawTicketId(Graphics2D g, String ticketId, int templateW, int templateH) {
        if (ticketId == null || ticketId.isBlank()) {
            return;
        }

        // Use anti-aliasing for text only.
        Object oldTextAa = g.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        Object oldAa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        try {
            String line1 = "Ticket Number:";

            FontRenderContext frc = g.getFontRenderContext();

            // Use Montserrat SemiBold if available, otherwise fall back.
            Font base = new Font("Arial", Font.PLAIN, 42);
            if (base.getFamily().equalsIgnoreCase("Dialog")) {
                base = new Font("Arial", Font.BOLD, 42);
            }
            Font font1 = base.deriveFont(Font.PLAIN, TICKET_LABEL_FONT_PX);
            Font font2 = base.deriveFont(Font.PLAIN, TICKET_VALUE_FONT_PX);

            TextLayout layout1 = new TextLayout(line1, font1, frc);
            TextLayout layout2 = new TextLayout(ticketId, font2, frc);

            double w1 = layout1.getBounds().getWidth();
            double h1 = layout1.getBounds().getHeight();
            double w2 = layout2.getBounds().getWidth();
            double h2 = layout2.getBounds().getHeight();

            for (int size = (int) TICKET_VALUE_FONT_PX; size >= TICKET_VALUE_MIN_FONT_PX && w2 > (TICKET_ID_W - 12); size--) {
                font2 = base.deriveFont((float) size);
                layout2 = new TextLayout(ticketId, font2, frc);
                w2 = layout2.getBounds().getWidth();
                h2 = layout2.getBounds().getHeight();
            }

            double lineGap = 20;
            double totalH = h1 + lineGap + h2;
            // Pull the ticket block down by 2% of the template height
            double startY = templateH - TICKET_ID_H - 50 + (TICKET_ID_H - totalH) / 2.0 + (templateH * 0.02);

            // Ticket box centered on X axis, then shifted 5% right.
            int boxX1 = (templateW - TICKET_ID_W) / 2;
            boxX1 += (int) Math.round(templateW * TICKET_RIGHT_SHIFT_FACTOR);
            float x1 = (float) (boxX1 + (TICKET_ID_W - w1) / 2.0);
            float y1 = (float) (startY - layout1.getBounds().getY());

            float x2 = (float) (boxX1 + (TICKET_ID_W - w2) / 2.0);
            float y2 = (float) (startY + h1 + lineGap - layout2.getBounds().getY());

            Color ticketGold = new Color(0xC5AE82);
            g.setColor(ticketGold);
            layout1.draw(g, x1, y1);
            layout2.draw(g, x2, y2);
        } finally {
            if (oldTextAa != null) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldTextAa);
            }
            if (oldAa != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAa);
            }
        }
    }


    private Path resolveOutputDir() {
        String configured = properties.outputDir();
        if (configured == null || configured.isBlank()) {
            // Avoid writing generated files into the repo/app working directory by default.
            // Use OS temp dir instead (safe for local dev and CI).
            return Paths.get(System.getProperty("java.io.tmpdir"), "actone-ticket-cards")
                    .toAbsolutePath()
                    .normalize();
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }
}
