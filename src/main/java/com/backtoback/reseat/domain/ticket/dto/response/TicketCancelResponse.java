package com.backtoback.reseat.domain.ticket.dto.response;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TicketCancelResponse {

    private Long ticketId;
    private TicketStatus status;
    private boolean refunded; // 환불 처리 여부
    private Integer refundAmount;
    private Long gameSeatId;
    private GameSeatStatus seatStatus;

    // Ticket 엔티티 기반 응답 생성
    public static TicketCancelResponse of(Ticket ticket, boolean refunded, Integer refundAmount) {
        return TicketCancelResponse
            .builder()
            .ticketId(ticket.getId())
            .status(ticket.getStatus())
            .refunded(refunded)
            .refundAmount(refundAmount)
            .gameSeatId(ticket.getGameSeat().getId())
            .seatStatus(ticket.getGameSeat().getStatus())
            .build();
    }
}
