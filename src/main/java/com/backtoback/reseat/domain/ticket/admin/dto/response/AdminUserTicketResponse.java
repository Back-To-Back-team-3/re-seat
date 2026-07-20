package com.backtoback.reseat.domain.ticket.admin.dto.response;

import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminUserTicketResponse {

    private Long ticketId;
    private String ticketNo;
    private TicketStatus status;     // ISSUED, USED, CANCELED
    private String qrToken;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime issuedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime usedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime canceledAt;

    // 경기 정보
    private Long gameId;
    private String gameTitle;
    private String stadiumName;
    private String homeTeamName;
    private String awayTeamName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime gameAt;

    // 좌석 정보
    private String seat;

    // 좌석 세부 정보
    private Long gameSeatId;
    private String zoneName;
    private String seatBlock;
    private String seatRow;
    private String seatNumber;

    public static AdminUserTicketResponse from(Ticket ticket) {
        var game = ticket.getGame();
        var gameSeat = ticket.getGameSeat();
        var seatEntity = gameSeat.getSeat();
        var zone = seatEntity.getZone();

        // 명세서 표기용 좌석 문자열 조합 (예: "1루 블루석 A-3-12")
        String formattedSeat = String.format("%s %s-%s-%s",
                zone != null ? zone.getName() : "",
                seatEntity.getSeatBlock(),
                seatEntity.getSeatRow(),
                seatEntity.getSeatNumber()
        ).trim();

        return AdminUserTicketResponse.builder()
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
