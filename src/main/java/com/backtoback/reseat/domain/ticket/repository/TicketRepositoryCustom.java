package com.backtoback.reseat.domain.ticket.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.backtoback.reseat.domain.admin.ticket.dto.request.TicketSearchCondition;
import com.backtoback.reseat.domain.ticket.entity.Ticket;

public interface TicketRepositoryCustom {

    Page<Ticket> searchTickets(TicketSearchCondition condition, Pageable pageable);
}
