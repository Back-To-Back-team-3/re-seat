package com.backtoback.reseat.domain.ticket.dto.response;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "내 티켓 취소 응답")
public class TicketCancelResponse {

    @Schema(
        description = "취소된 티켓 ID",
        example = "1001"
    )
    private Long ticketId;

    @Schema(
        description = "취소 후 티켓 상태",
        example = "CANCELED",
        allowableValues = {
            "ISSUED",
            "USED",
            "CANCELED"
        }
    )
    private TicketStatus status;

    @Schema(
        description = "환불 처리 여부",
        example = "true"
    )
    private boolean refunded; // 환불 처리 여부

    @Schema(
        description = "환불 금액",
        example = "18000",
        nullable = true
    )
    private Integer refundAmount;

    @Schema(
        description = "취소된 티켓의 경기 좌석 재고 ID",
        example = "5001"
    )
    private Long gameSeatId;

    @Schema(
        description = "취소 후 좌석 상태",
        example = "AVAILABLE"
    )
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
