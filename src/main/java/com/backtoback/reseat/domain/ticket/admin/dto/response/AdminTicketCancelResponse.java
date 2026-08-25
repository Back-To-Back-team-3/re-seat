package com.backtoback.reseat.domain.ticket.admin.dto.response;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketCancelReason;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "관리자 티켓 강제취소 응답")
public record AdminTicketCancelResponse(
    @Schema(
        description = "취소된 티켓 ID",
        example = "1001"
    ) Long ticketId,
    @Schema(
        description = "외부 노출용 티켓 번호",
        example = "TKT-20260711-3F2A9C"
    ) String ticketNo,
    @Schema(
        description = "취소 후 티켓 상태",
        example = "CANCELED",
        allowableValues = {
            "ISSUED",
            "USED",
            "CANCELED"
        }
    ) TicketStatus status,
    @Schema(
        description = "취소 사유 유형",
        example = "ADMIN_FORCE_CANCEL"
    ) TicketCancelReason cancelReason, // ADMIN_FORCE_CANCEL
    @Schema(
        description = "관리자가 입력한 상세 취소 사유",
        example = "매크로 부정 예매 탐지"
    ) String cancelDetail, // 상세 취소 사유 텍스트

    @Schema(
        description = "취소 시각",
        example = "2026-07-10 10:00:00"
    ) @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime canceledAt,

    @Schema(
        description = "취소된 티켓의 경기 좌석 재고 ID",
        example = "5001"
    ) Long gameSeatId,
    @Schema(
        description = "취소 후 좌석 상태",
        example = "AVAILABLE"
    ) GameSeatStatus seatStatus // AVAILABLE
) {
    public static AdminTicketCancelResponse from(Ticket ticket) {
        var gameSeat = ticket.getGameSeat();

        return AdminTicketCancelResponse
            .builder()
            .ticketId(ticket.getId())
            .ticketNo(ticket.getTicketNo())
            .status(ticket.getStatus())
            .cancelReason(ticket.getCancelReason())
            .cancelDetail(ticket.getCancelDetail())
            .canceledAt(ticket.getCanceledAt())
            .gameSeatId(gameSeat.getId())
            .seatStatus(gameSeat.getStatus())
            .build();
    }
}
