package com.prarambh.act.one.ticketing.service;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Generates a short, repeatable show identifier from a show name.
 *
 * <p>Contract:
 * <ul>
 *   <li>Output format: {@code SHOW-<SLUG>-<HASH>}</li>
 *   <li>{@code SLUG} is derived from show name (uppercase, A-Z/0-9 only, max 12 chars)</li>
 *   <li>{@code HASH} is a stable 6-char base36 CRC32 to reduce collision risk</li>
 * </ul>
 */
public final class ShowIdGenerator {

    private ShowIdGenerator() {
    }

    /**
     * Build a short show id for the given show name.
     *
     * @param showName human readable show name
     * @return generated show id
     */
    public static String fromShowName(String showName) {
        String normalized = normalize(showName);
        String slug = toSlug(normalized);
        String hash = crcBase36(normalized);
        return "SHOW-" + slug + "-" + hash;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        // Strip accents and normalize unicode punctuation.
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");

        // Treat common conjunction symbols as word separators so slugs combine words sensibly.
        // Example: "Chorabali & Gobhir" -> "Chorabali and Gobhir" -> slug "CHORABALIGOBHIR".
        n = n.replace("&", " and ")
                .replace("+", " and ");

        return n;
    }

    private static String toSlug(String s) {
        String upper = (s == null ? "" : s).toUpperCase(Locale.ROOT);
        // Keep only alphas/numbers, replace others with dash, collapse multiple dashes.
        String cleaned = upper.replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "")
                .replaceAll("-+", "-");
        if (cleaned.isBlank()) {
            cleaned = "EVENT";
        }
        // Keep it short for printing.
        cleaned = cleaned.replace("-", "");
        return cleaned.length() <= 12 ? cleaned : cleaned.substring(0, 12);
    }

    private static String crcBase36(String s) {
        CRC32 crc32 = new CRC32();
        crc32.update(s.getBytes(StandardCharsets.UTF_8));
        long value = crc32.getValue();
        String base36 = Long.toString(value, 36).toUpperCase(Locale.ROOT);
        // pad left to 6 chars for stable length
        if (base36.length() < 6) {
            return "000000".substring(base36.length()) + base36;
        }
        return base36.length() > 6 ? base36.substring(0, 6) : base36;
    }
}
