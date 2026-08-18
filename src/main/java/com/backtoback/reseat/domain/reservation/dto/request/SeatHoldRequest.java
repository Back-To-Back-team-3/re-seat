package com.backtoback.reseat.domain.reservation.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 좌석 선점 요청 DTO.
 * <p>gameSeatIds는 경기 좌석 재고(game_seats.id) 기준이다.
 * 주의: 물리 좌석(seats.id)이 아니다.
 */
@Schema(description = "좌석 선점(HOLD) 요청")
public record SeatHoldRequest(

    @Schema(
        description = "경기 ID",
        example = "1146"
    ) @NotNull(message = "gameId는 필수입니다.") @Positive(message = "gameId는 양수여야 합니다.") Long gameId,

    /**
     * 선점할 game_seats.id 목록. 1~2석 제한.
     * 근거: 단일 트랜잭션 처리 범위 + 매크로 대량 선점 방지.
     */
    @Schema(
        description = "선점할 경기 좌석 재고 ID 목록 (game_seats.id)",
        example = "[5001, 5002]"
    )
    @NotEmpty(message = "gameSeatIds는 1개 이상이어야 합니다.")
    @Size(
        min = 1,
        max = 2,
        message = "좌석은 최소 1개, 최대 2개까지 선점 가능합니다."
    ) List<@NotNull @Positive Long> gameSeatIds
) {
}
