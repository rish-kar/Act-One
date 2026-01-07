package com.prarambh.act.one.ticketing.service.card;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.prarambh.act.one.ticketing.model.Ticket;
import jakarta.annotation.PostConstruct;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * Generates a "ticket card" JPG by drawing a scannable code containing {@code qrCodeId}
 * (QR code) and placing it onto a template image.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketCardGenerator {

    // NOTE: Card.jpg is the recommended poster template (coordinates below are based on that).
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

    // Cached template to avoid reloading the large image for each ticket
    private volatile BufferedImage cachedTemplate;
    private final Object templateLock = new Object();

    // Cached JPG template to avoid reloading/decoding for every ticket.
    private volatile BufferedImage cachedJpgTemplate;
    private final Object jpgTemplateLock = new Object();

    /**
     * Pre-load the template at startup to fail fast if there are issues
     * and to ensure ImageIO plugins are initialized.
     */
    @PostConstruct
    public void init() {
        if (!properties.enabled()) {
            log.info("event=ticket_card_generator_disabled");
            return;
        }

        try {
            // Initialize ImageIO scanners to avoid lazy loading issues
            log.info("event=imageio_init_starting");
            javax.imageio.ImageIO.scanForPlugins();
            String[] readerFormats = javax.imageio.ImageIO.getReaderFormatNames();
            String[] writerFormats = javax.imageio.ImageIO.getWriterFormatNames();
            log.info("event=imageio_init_complete readerFormats={} writerFormats={}",
                    String.join(",", readerFormats), String.join(",", writerFormats));

            // Pre-load the template at startup
            log.info("event=template_preload_starting");
            loadTemplate();
            log.info("event=template_preload_complete");
        } catch (Exception e) {
            log.error("event=template_preload_failed error={}", e, e);
            // Don't throw - allow app to start, but email card generation will fail
        }
    }

    /**
     * Generate a ticket card as JPEG bytes (baseline JPEG).
     *
     * <p>This is the preferred method for email attachments: it avoids filesystem writes and is much faster in
     * containerized environments.
     */
    public byte[] generateTicketCardJpegBytes(Ticket ticket) {
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

        BufferedImage template = loadJpgTemplate();

        int templateW = template.getWidth();
        int templateH = template.getHeight();

        int qrRenderW = (int) Math.round(QR_BOX_W * QR_SCALE_FACTOR);
        int qrRenderH = (int) Math.round(QR_BOX_H * QR_SCALE_FACTOR);
        int qrX = (templateW - qrRenderW) / 2 + (int) Math.round(templateW * QR_X_RIGHT_FACTOR);
        int qrY = (int) Math.round(QR_Y_BASE - (QR_BOX_H * QR_Y_UP_FACTOR));

        BufferedImage qr = renderQrCode(ticket.getQrCodeId(), qrRenderW, qrRenderH);

        // Compose into RGB directly (JPEG output)
        BufferedImage out = new BufferedImage(templateW, templateH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

            g.drawImage(template, 0, 0, null);
            g.drawImage(qr, qrX, qrY, null);
            drawTicketId(g, ticket.getTicketId().toString(), templateW, templateH);
        } finally {
            g.dispose();
        }

        return encodeJpeg(out, 0.88f);
    }

    private BufferedImage loadTemplate() {
        // Double-checked locking for thread-safe lazy initialization
        BufferedImage template = cachedTemplate;
        if (template != null) {
            return template;
        }

        synchronized (templateLock) {
            template = cachedTemplate;
            if (template != null) {
                return template;
            }

            String resourcePath = properties.templateResource() == null || properties.templateResource().isBlank()
                    ? "classpath:/static/Card.jpg"
                    : properties.templateResource();

            Resource resource = resourceLoader.getResource(resourcePath);
            try {
                if (!resource.exists()) {
                    throw new IllegalStateException("Template resource does not exist: " + resourcePath);
                }
                try (InputStream is = resource.getInputStream()) {
                    if (is == null) {
                        throw new IllegalStateException("Template resource input stream is null: " + resourcePath);
                    }
                    // Read all bytes first to avoid stream issues
                    byte[] imageBytes = is.readAllBytes();
                    log.info("event=template_resource_loaded resourcePath={} sizeBytes={}", resourcePath, imageBytes.length);

                    log.info("event=template_decode_starting resourcePath={}", resourcePath);
                    BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageBytes));
                    log.info("event=template_decode_finished resourcePath={} result={}", resourcePath, img != null ? "success" : "null");

                    if (img == null) {
                        // Log available ImageIO readers for debugging
                        String[] readerFormats = javax.imageio.ImageIO.getReaderFormatNames();
                        log.error("event=template_decode_failed resourcePath={} availableFormats={}", resourcePath, String.join(",", readerFormats));
                        throw new IllegalStateException("Template image could not be decoded (ImageIO returned null): " + resourcePath);
                    }
                    log.info("event=template_image_decoded resourcePath={} width={} height={}", resourcePath, img.getWidth(), img.getHeight());

                    // Cache the template for reuse
                    cachedTemplate = img;
                    return img;
                }
            } catch (IOException e) {
                log.error("event=template_load_io_error resourcePath={} error={}", resourcePath, e, e);
                throw new IllegalStateException("Failed to load template image: " + resourcePath, e);
            }
        }
    }

    private BufferedImage loadJpgTemplate() {
        BufferedImage template = cachedJpgTemplate;
        if (template != null) {
            return template;
        }

        synchronized (jpgTemplateLock) {
            template = cachedJpgTemplate;
            if (template != null) {
                return template;
            }

            // Prefer Card.jpg if present; fall back to the configured template if missing/invalid.
            String preferred = "classpath:/static/Card.jpg";
            Resource preferredRes = resourceLoader.getResource(preferred);

            try {
                if (preferredRes.exists()) {
                    try (InputStream is = preferredRes.getInputStream()) {
                        byte[] bytes = is.readAllBytes();
                        log.info("event=template_resource_loaded resourcePath={} sizeBytes={}", preferred, bytes.length);
                        BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                        if (img != null) {
                            cachedJpgTemplate = img;
                            return img;
                        }
                        log.warn("event=template_decode_failed resourcePath={} reason=imageio_null", preferred);
                    }
                } else {
                    log.warn("event=template_resource_missing resourcePath={}", preferred);
                }
            } catch (Exception e) {
                log.warn("event=template_load_failed resourcePath={} error={}", preferred, e.toString());
            }

            // Fall back to existing template logic (png or configured) to avoid hard failure.
            template = loadTemplate();
            cachedJpgTemplate = template;
            return template;
        }
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

            // Revert to the original, predictable font choice.
            // (Do not rely on optional system fonts that may differ across machines/containers.)
            Font base = new Font("Arial", Font.BOLD, 42);
            Font font1 = base.deriveFont(Font.BOLD, TICKET_LABEL_FONT_PX);
            Font font2 = base.deriveFont(Font.BOLD, TICKET_VALUE_FONT_PX);

            TextLayout layout1 = new TextLayout(line1, font1, frc);
            TextLayout layout2 = new TextLayout(ticketId, font2, frc);

            double w1 = layout1.getBounds().getWidth();
            double h1 = layout1.getBounds().getHeight();
            double w2 = layout2.getBounds().getWidth();
            double h2 = layout2.getBounds().getHeight();

            for (int size = (int) TICKET_VALUE_FONT_PX; size >= TICKET_VALUE_MIN_FONT_PX && w2 > (TICKET_ID_W - 12); size--) {
                font2 = base.deriveFont(Font.BOLD, (float) size);
                layout2 = new TextLayout(ticketId, font2, frc);
                w2 = layout2.getBounds().getWidth();
                h2 = layout2.getBounds().getHeight();
            }

            double lineGap = 20;
            double totalH = h1 + lineGap + h2;
            // Pull the ticket block down by 2% of the template height
            double startY = templateH - TICKET_ID_H - 50 + (TICKET_ID_H - totalH) / 2.0 + (templateH * 0.02);

            // Ticket box centered on X axis.
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

    private static byte[] encodeJpeg(BufferedImage rgb, float quality) {
        try {
            Iterator<ImageWriter> writers = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                writers = javax.imageio.ImageIO.getImageWritersByFormatName("jpg");
            }
            if (!writers.hasNext()) {
                throw new IllegalStateException("No ImageIO writer available for jpeg/jpg");
            }

            ImageWriter writer = writers.next();
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(256 * 1024);
                try (ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    if (param.canWriteCompressed()) {
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(Math.max(0.1f, Math.min(1.0f, quality)));
                    }
                    writer.write(null, new IIOImage(rgb, null, null), param);
                    ios.flush();
                }
                return baos.toByteArray();
            } finally {
                writer.dispose();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode JPEG", e);
        }
    }
}
