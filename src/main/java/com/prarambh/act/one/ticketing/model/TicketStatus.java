package com.prarambh.act.one.ticketing.model;

/**
 * Ticket lifecycle status.
 */
public enum TicketStatus {
    /** Transaction/payment is recorded but tickets are not yet issued (manual validation pending). */
    TRANSACTION_MADE,
    /** Payment will be collected in cash later; record exists but is pending payment. */
    TRANSACTION_PENDING,
    /** Ticket has been issued and is eligible for check-in. */
    ISSUED,
    /** Ticket has been checked-in and cannot be used again. */
    USED
}
