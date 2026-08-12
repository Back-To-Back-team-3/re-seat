package com.backtoback.reseat.domain.ticket.dto.response;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 티켓 검표 응답 DTO
 * <p>
 * API 명세 8.4의 응답 예시 기반
 * 검표 완료된 티켓의 상태, 사용 시각, 좌석 정보, 예매자 이름을 포함
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TicketVerifyResponse {

    private Long ticketId;

    // 검표 후 티켓 상태 (보통 USED 상태)
    private TicketStatus status;

    private LocalDateTime usedAt;
    private String seat;

    // 티켓 소유자 이름
    private String holderName;

    /**
     * 티켓 엔티티를 검표 응답 DTO로 변환
     *
     * @param ticket 검표 완료된 티켓 엔티티
     * @return 티켓 검표 응답 DTO
     */
    public static TicketVerifyResponse from(Ticket ticket) {
        return TicketVerifyResponse
            .builder()
            .ticketId(ticket.getId())
            .status(ticket.getStatus())
            .usedAt(ticket.getUsedAt())
            .seat(buildSeatLabel(ticket))
            .holderName(ticket.getUser().getName())
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
        return ticket.getGameSeat().getSeat().getZone().getName() + " " + ticket.getGameSeat().getSeat().getSeatBlock()
            + "-" + ticket.getGameSeat().getSeat().getSeatRow() + "-" + ticket.getGameSeat().getSeat().getSeatNumber();
    }
}
