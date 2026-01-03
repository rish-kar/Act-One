package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Ticket;
import java.util.List;

/**
 * Event published when tickets have been checked-in.
 *
 * <p>Contains the tickets that were checked-in as part of the operation.
 */
public final class TicketPurchaseCheckedInEvent {

    private final List<Ticket> tickets;

    /**
     * Construct the event.
     *
     * @param tickets list of tickets checked-in; must not be null.
     */
    public TicketPurchaseCheckedInEvent(List<Ticket> tickets) {
        this.tickets = List.copyOf(tickets);
    }

    /**
     * Returns the tickets associated with this check-in event.
     *
     * @return unmodifiable list of tickets.
     */
    public List<Ticket> getTickets() { return tickets; }
}
