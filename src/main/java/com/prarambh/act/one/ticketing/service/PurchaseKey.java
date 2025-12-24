package com.prarambh.act.one.ticketing.service;

/**
 * Best-effort grouping key for tickets that belong to the same "purchase".
 *
 * <p>We don't currently have a purchaseId in the DB, so we group by stable customer + show fields.
 */
public record PurchaseKey(String email, String phoneNumber, String showId, String showName) {

    public static PurchaseKey from(String email, String phoneNumber, String showId, String showName) {
        return new PurchaseKey(
                normalize(email),
                normalize(phoneNumber),
                normalize(showId),
                normalize(showName)
        );
    }

    private static String normalize(String s) {
        return s == null ? null : s.trim().toLowerCase();
    }
}

