package com.prarambh.act.one.ticketing.model;

/**
 * Ticket lifecycle status.
 */
public enum TicketStatus {
    /** Ticket has been issued and is eligible for check-in. */
    ISSUED,
    /** Ticket has been checked-in and cannot be used again. */
    USED
}
