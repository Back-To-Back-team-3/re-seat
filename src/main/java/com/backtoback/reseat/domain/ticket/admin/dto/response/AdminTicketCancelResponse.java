package com.backtoback.reseat.domain.ticket.admin.dto.response;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketCancelReason;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Builder;

@Builder
public record AdminTicketCancelResponse(
	Long ticketId,
	String ticketNo,
	TicketStatus status,
	TicketCancelReason cancelReason, // ADMIN_FORCE_CANCEL
	String cancelDetail, // 상세 취소 사유 텍스트

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	LocalDateTime canceledAt,

	Long gameSeatId,
	GameSeatStatus seatStatus // AVAILABLE
) {
	public static AdminTicketCancelResponse from(Ticket ticket) {
		var gameSeat = ticket.getGameSeat();

		return AdminTicketCancelResponse.builder()
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
