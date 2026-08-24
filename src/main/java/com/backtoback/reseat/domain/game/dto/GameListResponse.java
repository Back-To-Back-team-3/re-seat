package com.backtoback.reseat.domain.game.dto;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.stadium.dto.StadiumSummaryResponse;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record GameListResponse(
    Long gameId,
    String title,
    TeamSummaryResponse homeTeam,
    TeamSummaryResponse awayTeam,
    StadiumSummaryResponse stadium,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime gameAt,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime bookingOpenAt,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime bookingCloseAt,
    BookingStatus bookingStatus
) {

    /**
     * Game 엔티티를 경기 목록 응답 DTO로 변환한다.
     *
     * @param game 조회된 경기 엔티티
     * @return 경기 목록 응답 DTO
     */
    public static GameListResponse from(Game game) {
        return new GameListResponse(
            game.getId(),
            game.getTitle(),
            TeamSummaryResponse.from(game.getHomeTeam()),
            TeamSummaryResponse.from(game.getAwayTeam()),
            StadiumSummaryResponse.from(game.getStadium()),
            game.getGameAt(),
            game.getBookingOpenAt(),
            game.getBookingCloseAt(),
            game.getBookingStatus()
        );
    }

    /**
     * 경기 응답 내부에서 사용하는 구단 요약 응답 DTO.
     */
    public record TeamResponse(Long teamId, String name) {

        public static TeamResponse from(Long teamId, String name) {
            return new TeamResponse(teamId, name);
        }
    }
}
