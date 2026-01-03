package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Ticket;
import java.util.List;

/**
 * Event fired after individual tickets are issued. This event wraps the list of issued
 * tickets and is consumed by listeners that send out emails or perform other side-effects.
 */
public final class TicketIssuedEvent {

    private final List<Ticket> tickets;

    /**
     * Create the event.
     *
     * @param tickets tickets that were issued; must not be null.
     */
    public TicketIssuedEvent(List<Ticket> tickets) {
        this.tickets = List.copyOf(tickets);
    }

    /**
     * Returns the issued tickets.
     *
     * @return unmodifiable list of tickets.
     */
    public List<Ticket> getTickets() { return tickets; }
}
