package com.prarambh.act.one.ticketing.service;

/**
 * Best-effort grouping key for tickets that belong to the same "purchase".
 *
 * <p>We don't currently have a purchaseId in the DB, so we group by stable customer + show fields.
 */
public record PurchaseKey(String email, String phoneNumber, String showId, String showName) {

    /**
     * Build a PurchaseKey from the raw fields applying normalization.
     *
     * @param email purchaser email
     * @param phoneNumber purchaser phone
     * @param showId show id
     * @param showName show name
     * @return normalized PurchaseKey
     */
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
