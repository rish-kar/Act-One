package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Ticket;
import java.util.List;

/**
 * Event published when a purchase has been marked as issued.
 *
 * <p>Contains an immutable list of tickets that were issued as part of the purchase.
 */
public final class TicketPurchaseIssuedEvent {

    private final List<Ticket> tickets;

    /**
     * Create a new event.
     *
     * @param tickets list of tickets that were issued; must not be null.
     */
    public TicketPurchaseIssuedEvent(List<Ticket> tickets) {
        this.tickets = List.copyOf(tickets);
    }

    /**
     * Returns the list of tickets associated with this event.
     *
     * @return unmodifiable list of tickets.
     */
    public List<Ticket> getTickets() { return tickets; }
}
