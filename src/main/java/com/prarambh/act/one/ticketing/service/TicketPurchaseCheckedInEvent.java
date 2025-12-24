package com.prarambh.act.one.ticketing.service;

import com.prarambh.act.one.ticketing.model.Ticket;
import java.util.List;

/** Domain event published after a purchase (one request) checks in one or more tickets. */
public record TicketPurchaseCheckedInEvent(List<Ticket> tickets) {
}

