package com.backtoback.reseat.domain.ticket.dto.response;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "내 티켓 상세 정보")
public class TicketDetailResponse {

    @Schema(
        description = "티켓 ID",
        example = "1001"
    )
    private Long ticketId;

    @Schema(
        description = "외부 노출용 티켓 번호",
        example = "TKT-20260711-3F2A9C"
    )
    private String ticketNo;

    @Schema(
        description = "경기 ID",
        example = "1"
    )
    private Long gameId;

    @Schema(
        description = "경기 좌석 재고 ID",
        example = "5001"
    )
    private Long gameSeatId;

    @Schema(
        description = "좌석 표시 문자열",
        example = "1루 A-3-12"
    )
    private String seat;

    @Schema(
        description = "티켓 상태",
        example = "ISSUED",
        allowableValues = {
            "ISSUED",
            "USED",
            "CANCELED"
        }
    )
    private TicketStatus status;

    @Schema(
        description = "입장 검증용 QR 토큰",
        example = "8f14e45f-ea5e-4a2f-b3d2-09bcdb51f321"
    )
    private String qrToken;

    @Schema(
        description = "경기 일시",
        example = "2026-07-11T18:30:00"
    )
    private LocalDateTime gameAt;

    /**
     * 티켓 엔티티를 상세 응답 DTO로 변환
     *
     * @param ticket 티켓 엔티티
     * @return 티켓 상세 응답 DTO
     */
    public static TicketDetailResponse from(Ticket ticket) {
        return TicketDetailResponse
            .builder()
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
        return ticket.getGameSeat().getSeat().getZone().getName() + " " + ticket.getGameSeat().getSeat().getSeatBlock()
            + "-" + ticket.getGameSeat().getSeat().getSeatRow() + "-" + ticket.getGameSeat().getSeat().getSeatNumber();
    }
}
