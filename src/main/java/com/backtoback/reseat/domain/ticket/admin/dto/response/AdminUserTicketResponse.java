package com.backtoback.reseat.domain.ticket.admin.dto.response;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "관리자용 사용자 티켓 소유 목록 항목")
public record AdminUserTicketResponse(
    @Schema(
        description = "티켓 ID",
        example = "1001"
    ) Long ticketId,
    @Schema(
        description = "외부 노출용 티켓 번호",
        example = "TKT-20260711-3F2A9C"
    ) String ticketNo,
    @Schema(
        description = "티켓 상태",
        example = "ISSUED",
        allowableValues = {
            "ISSUED",
            "USED",
            "CANCELED"
        }
    ) TicketStatus status,
    @Schema(
        description = "입장 검증용 QR 토큰",
        example = "8f14e45f-ea5e-4a2f-b3d2-09bcdb51f321"
    ) String qrToken,

    @Schema(
        description = "티켓 발급 시각",
        example = "2026-07-04 14:00:00"
    ) @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime issuedAt,

    @Schema(
        description = "티켓 사용(입장) 시각. 미사용 시 null",
        example = "2026-07-11 18:25:00",
        nullable = true
    ) @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime usedAt,

    @Schema(
        description = "티켓 취소 시각. 미취소 시 null",
        example = "2026-07-10 10:00:00",
        nullable = true
    ) @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime canceledAt,

    // 경기 정보
    @Schema(
        description = "경기 ID",
        example = "1"
    ) Long gameId,
    @Schema(
        description = "경기명",
        example = "LG vs 한화"
    ) String gameTitle,
    @Schema(
        description = "구장명",
        example = "잠실야구장"
    ) String stadiumName,
    @Schema(
        description = "홈팀명",
        example = "LG"
    ) String homeTeamName,
    @Schema(
        description = "원정팀명",
        example = "한화"
    ) String awayTeamName,

    @Schema(
        description = "경기 일시",
        example = "2026-07-11 18:30:00"
    ) @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime gameAt,

    // 좌석 정보 (명세서 통합 표기 format: "1루 블루석 A-3-12")
    @Schema(
        description = "좌석 표시 문자열",
        example = "1루 블루석 A-3-12"
    ) String seat,

    // 좌석 세부 정보
    @Schema(
        description = "경기 좌석 재고 ID",
        example = "5001"
    ) Long gameSeatId,
    @Schema(
        description = "좌석 구역명",
        example = "1루 블루석"
    ) String zoneName,
    @Schema(
        description = "좌석 블록",
        example = "A"
    ) String seatBlock,
    @Schema(
        description = "좌석 열",
        example = "3"
    ) String seatRow,
    @Schema(
        description = "좌석 번호",
        example = "12"
    ) String seatNumber
) {
    public static AdminUserTicketResponse from(Ticket ticket) {
        var game = ticket.getGame();
        var gameSeat = ticket.getGameSeat();
        var seatEntity = gameSeat.getSeat();
        var zone = seatEntity.getZone();

        // 명세서 표기용 좌석 문자열 조합 (예: "1루 블루석 A-3-12")
        String formattedSeat
            = String
                .format(
                    "%s %s-%s-%s",
                    zone != null ? zone.getName() : "",
                    seatEntity.getSeatBlock(),
                    seatEntity.getSeatRow(),
                    seatEntity.getSeatNumber()
                )
                .trim();

        return AdminUserTicketResponse
            .builder()
            .ticketId(ticket.getId())
            .ticketNo(ticket.getTicketNo())
            .status(ticket.getStatus())
            .qrToken(ticket.getQrToken())
            .issuedAt(ticket.getIssuedAt())
            .usedAt(ticket.getUsedAt())
            .canceledAt(ticket.getCanceledAt())
            .gameId(game.getId())
            .gameTitle(game.getTitle())
            .stadiumName(game.getStadium() != null ? game.getStadium().getName() : null)
            .homeTeamName(game.getHomeTeam() != null ? game.getHomeTeam().getName() : null)
            .awayTeamName(game.getAwayTeam() != null ? game.getAwayTeam().getName() : null)
            .gameAt(game.getGameAt())
            .seat(formattedSeat)
            .gameSeatId(gameSeat.getId())
            .zoneName(zone != null ? zone.getName() : null)
            .seatBlock(seatEntity.getSeatBlock())
            .seatRow(seatEntity.getSeatRow())
            .seatNumber(seatEntity.getSeatNumber())
            .build();
    }
}
