package com.backtoback.reseat.domain.seatinventory.dto;

import com.backtoback.reseat.domain.stadium.entity.SeatGrade;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 구역 요약 응답.
 *
 * <p>프론트 구역 선택 화면 렌더링용. 구역별 잔여 좌석 수를 실시간으로 집계해 반환한다.
 * 10건(구역 수) 리스트로 반환된다.
 *
 * <p>totalCount는 현재 50으로 고정된다.
 * V4 시드 기준 구역당 5행(A~E) × 10열(1~10) = 50석.
 * 구장·구역 구성이 바뀌면 집계 쿼리로 교체해야 한다.
 */
@Schema(description = "구역 요약 응답")
public record ZoneSummaryResponse(

    @Schema(description = "구역 ID", example = "1")
    Long zoneId,

    @Schema(description = "구역명", example = "1루 101")
    String zoneName,

    @Schema(description = "좌석 등급", example = "INFIELD")
    SeatGrade grade,

    @Schema(description = "구역 기본가 (화~목 기준, 원)", example = "18000")
    int basePrice,

    @Schema(description = "구역 전체 좌석 수", example = "50")
    int totalCount,

    @Schema(description = "잔여 좌석 수 (AVAILABLE)", example = "42")
    int availableCount
) {
}
