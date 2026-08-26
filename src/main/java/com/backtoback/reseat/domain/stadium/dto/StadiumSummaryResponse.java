package com.backtoback.reseat.domain.stadium.dto;

import com.backtoback.reseat.domain.stadium.entity.Stadium;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 경기 목록 응답에 중첩되는 구장 요약 정보.
 * <p>프론트가 구장 상세로 이동할 수 있도록 name과 함께 stadiumId를 노출한다.
 */
@Schema(description = "구장 요약 정보")
public record StadiumSummaryResponse(
    @Schema(
        description = "구장 식별자",
        example = "1"
    ) Long stadiumId,
    @Schema(
        description = "구장명",
        example = "서울종합운동장 야구장"
    ) String name
) {
    public static StadiumSummaryResponse from(Stadium stadium) {
        return new StadiumSummaryResponse(stadium.getId(), stadium.getName());
    }
}
