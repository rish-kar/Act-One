package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Ticket;

/** Domain event published after a ticket is successfully issued (persisted). */
public record TicketIssuedEvent(Ticket ticket) {
}

