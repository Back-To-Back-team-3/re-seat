package com.backtoback.reseat.domain.admin.game.dto.response;

import com.backtoback.reseat.domain.game.entity.BookingStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 경기 예매 상태 전이 응답 DTO
 */
@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "경기 예매 상태 전이 응답")
public class GameBookingStatusResponse {

    @Schema(
        description = "경기 ID",
        example = "10"
    )
    private final Long gameId;

    @Schema(
        description = "변경된 예매 상태",
        example = "OPEN"
    )
    private final BookingStatus bookingStatus;

    public static GameBookingStatusResponse from(Long gameId, BookingStatus bookingStatus) {
        return GameBookingStatusResponse.builder().gameId(gameId).bookingStatus(bookingStatus).build();
    }
}
