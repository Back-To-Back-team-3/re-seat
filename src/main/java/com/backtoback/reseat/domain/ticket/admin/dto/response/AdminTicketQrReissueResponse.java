package com.backtoback.reseat.domain.ticket.admin.dto.response;

import com.backtoback.reseat.domain.ticket.entity.Ticket;

import lombok.Builder;

@Builder
public record AdminTicketQrReissueResponse(Long ticketId, String qrToken) {

    public static AdminTicketQrReissueResponse from(Ticket ticket) {
        return AdminTicketQrReissueResponse.builder().ticketId(ticket.getId()).qrToken(ticket.getQrToken()).build();
    }
}
