package com.backtoback.reseat.domain.seatinventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 경기 좌석 상태 요약 응답.
 * <p>관리자 화면 상단 요약 카드 렌더링용. 경기 전체 기준 상태별 합계만 담으며, 구역별 세분화는 포함하지 않는다.
 */
@Schema(description = "경기 좌석 상태 요약 응답")
public record SeatInventorySummaryResponse(

    @Schema(
        description = "판매 가능 좌석 수",
        example = "18240"
    ) long available,

    @Schema(
        description = "임시 선점 좌석 수",
        example = "312"
    ) long held,

    @Schema(
        description = "판매 완료 좌석 수",
        example = "6410"
    ) long sold,

    @Schema(
        description = "관리자 차단 좌석 수",
        example = "38"
    ) long blocked
) {
}
