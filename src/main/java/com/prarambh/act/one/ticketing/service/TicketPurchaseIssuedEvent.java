package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Ticket;
import java.util.List;

/** Domain event published after a purchase (one request) issues one or more tickets. */
public record TicketPurchaseIssuedEvent(List<Ticket> tickets) {
}

