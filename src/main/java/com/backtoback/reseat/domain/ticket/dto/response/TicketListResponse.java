package com.backtoback.reseat.domain.ticket.dto.response;

import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 티켓 목록 조회 응답 DTO
 * <p>
 * API 명세 8.1의 응답 예시 기반
 * 사용자가 보유한 티켓의 기본 정보와 경기 정보를 포함
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TicketListResponse {

    private Long ticketId;
    private String ticketNo;
    private Long gameId;
    private String seat;
    private TicketStatus status;
    private String qrToken;
    private LocalDateTime gameAt;

    /**
     * 티켓 엔티티를 목록 응답 DTO로 변환
     *
     * @param ticket 티켓 엔티티
     * @return 티켓 목록 응답 DTO
     */
    public static TicketListResponse from(Ticket ticket) {
        return TicketListResponse.builder()
            .ticketId(ticket.getId())
            .ticketNo(ticket.getTicketNo())
            .gameId(ticket.getGame().getId())
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
