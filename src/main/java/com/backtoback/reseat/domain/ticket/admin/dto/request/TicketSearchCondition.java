package com.backtoback.reseat.domain.ticket.admin.dto.request;

import java.time.LocalDate;

import com.backtoback.reseat.domain.ticket.entity.TicketStatus;

public record TicketSearchCondition(Long userId, TicketStatus status, LocalDate gameDateFrom, LocalDate gameDateTo) {
}
