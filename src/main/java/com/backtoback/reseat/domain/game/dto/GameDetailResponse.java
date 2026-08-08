package com.backtoback.reseat.domain.game.dto;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 경기 상세 조회 응답 DTO.
 *
 * <p>사용자가 특정 경기를 선택했을 때 예매 진입 판단에 필요한 경기 상세 정보를 제공한다.
 * 좌석 재고와 가격 정보는 포함하지 않고, 경기 기준 정보만 반환한다.</p>
 */
public record GameDetailResponse(
    Long gameId,
    String title,
    TeamSummaryResponse homeTeam,
    TeamSummaryResponse awayTeam,
    StadiumResponse stadium,

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime gameAt,

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime bookingOpenAt,

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime bookingCloseAt,

    BookingStatus bookingStatus
) {

    /**
     * Game 엔티티를 경기 상세 응답 DTO로 변환한다.
     *
     * @param game 조회된 경기 엔티티
     * @return 경기 상세 응답 DTO
     */
    public static GameDetailResponse from(Game game) {
        return new GameDetailResponse(
            game.getId(),
            game.getTitle(),
            TeamSummaryResponse.from(game.getHomeTeam()),
            TeamSummaryResponse.from(game.getAwayTeam()),
            StadiumResponse.from(game.getStadium()),
            game.getGameAt(),
            game.getBookingOpenAt(),
            game.getBookingCloseAt(),
            game.getBookingStatus()
        );
    }

    /**
     * 경기 상세 응답 전용 구장 DTO.
     */
    public record StadiumResponse(
        Long stadiumId,
        String name,
        String address,
        int totalCapacity
    ) {
        public static StadiumResponse from(Stadium stadium) {
            return new StadiumResponse(
                stadium.getId(),
                stadium.getName(),
                stadium.getAddress(),
                stadium.getTotalCapacity()
            );
        }
    }
}
