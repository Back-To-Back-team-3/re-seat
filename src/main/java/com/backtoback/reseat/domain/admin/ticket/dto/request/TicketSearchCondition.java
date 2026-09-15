package com.backtoback.reseat.domain.admin.ticket.dto.request;

import java.time.LocalDate;

import com.backtoback.reseat.domain.ticket.entity.TicketStatus;

public record TicketSearchCondition(Long userId, TicketStatus status, LocalDate gameDateFrom, LocalDate gameDateTo) {
}
