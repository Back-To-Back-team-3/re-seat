package com.backtoback.reseat.domain.ticket.dto.response;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 티켓 상세 조회 응답 DTO
 * <p>
 * API 명세 8.2의 응답 예시 기반
 * 티켓의 기본 정보, 좌석 정보, QR 토큰, 경기 시작 일시를 포함
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TicketDetailResponse {

	private Long ticketId;
	private String ticketNo;
	private Long gameId;
	private Long gameSeatId;
	private String seat;
	private TicketStatus status;
	private String qrToken;
	private LocalDateTime gameAt;

	/**
	 * 티켓 엔티티를 상세 응답 DTO로 변환
	 *
	 * @param ticket 티켓 엔티티
	 * @return 티켓 상세 응답 DTO
	 */
	public static TicketDetailResponse from(Ticket ticket) {
		return TicketDetailResponse.builder()
			.ticketId(ticket.getId())
			.ticketNo(ticket.getTicketNo())
			.gameId(ticket.getGame().getId())
			.gameSeatId(ticket.getGameSeat().getId())
			.seat(buildSeatLabel(ticket))
			.status(ticket.getStatus())
			.qrToken(ticket.getQrToken())
			.gameAt(ticket.getGame().getGameAt())
			.build();
	}

	/**
	 * 티켓의 좌석 정보를 사용자 표시용 문자열로 조합
	 * 예:"1루 A-3-12"
	 *
	 * @param ticket 티켓 엔티티
	 * @return 좌석 라벨 문자열
	 */
	private static String buildSeatLabel(Ticket ticket) {
		return ticket.getGameSeat().getSeat().getZone().getName() + " "
			+ ticket.getGameSeat().getSeat().getSeatBlock() + "-"
			+ ticket.getGameSeat().getSeat().getSeatRow() + "-"
			+ ticket.getGameSeat().getSeat().getSeatNumber();
	}
}
