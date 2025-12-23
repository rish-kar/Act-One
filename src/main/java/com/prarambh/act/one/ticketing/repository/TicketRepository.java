package com.prarambh.act.one.ticketing.repository;

import com.prarambh.act.one.ticketing.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
}
