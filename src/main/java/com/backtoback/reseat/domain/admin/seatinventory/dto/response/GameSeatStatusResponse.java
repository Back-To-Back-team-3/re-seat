package com.backtoback.reseat.domain.admin.seatinventory.dto.response;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 좌석 상태 변경(차단·해제) 응답 DTO.
 */
@Schema(description = "좌석 상태 변경 응답")
public record GameSeatStatusResponse(

    @Schema(
        description = "경기 좌석 재고 ID",
        example = "5001"
    ) Long gameSeatId,

    @Schema(
        description = "변경된 좌석 상태",
        example = "BLOCKED"
    ) GameSeatStatus status
) {
}
