package com.prarambh.act.one.ticketing.service.quotes;

/** Parsed theatre quote entry. */
public record TheatreQuote(String key, String text, String author) {

    public static TheatreQuote fromProperty(String key, String value) {
        // Expected: "<quote> — <author>" (em dash) or "<quote> - <author>".
        if (value == null) {
            return new TheatreQuote(key, "", "");
        }

        String v = value.trim();
        String[] parts;
        if (v.contains(" — ")) {
            parts = v.split(" — ", 2);
        } else if (v.contains(" – ")) {
            parts = v.split(" – ", 2);
        } else if (v.contains(" - ")) {
            parts = v.split(" - ", 2);
        } else {
            parts = new String[]{v, ""};
        }

        String text = parts.length > 0 ? parts[0].trim() : "";
        String author = parts.length > 1 ? parts[1].trim() : "";
        return new TheatreQuote(key, text, author);
    }

    public String formatted() {
        if (author == null || author.isBlank()) {
            return "\"" + text + "\"";
        }
        // Use a plain hyphen here for maximum compatibility across SMTP gateways/clients.
        return "\"" + text + "\" - " + author;
    }
}
