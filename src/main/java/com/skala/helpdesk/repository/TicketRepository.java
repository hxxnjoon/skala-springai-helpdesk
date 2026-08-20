package com.skala.helpdesk.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.skala.helpdesk.domain.Ticket;
import com.skala.helpdesk.domain.Ticket.Status;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByStatusOrderByRequestedAtAsc(Status status);
}
