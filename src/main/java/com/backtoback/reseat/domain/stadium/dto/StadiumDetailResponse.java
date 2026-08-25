package com.backtoback.reseat.domain.stadium.dto;

import com.backtoback.reseat.domain.stadium.entity.Stadium;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 구장 상세 정보 응답.
 * <p>경기 상세 조회 응답에 중첩된다.
 */
@Schema(description = "구장 상세 정보")
public record StadiumDetailResponse(
    @Schema(
        description = "구장 식별자",
        example = "1"
    ) Long stadiumId,
    @Schema(
        description = "구장명",
        example = "서울종합운동장 야구장"
    ) String name,
    @Schema(
        description = "구장 주소",
        example = "서울 송파구"
    ) String address,
    @Schema(
        description = "총 수용 인원",
        example = "23750"
    ) int totalCapacity
) {
    public static StadiumDetailResponse from(Stadium stadium) {
        return new StadiumDetailResponse(
            stadium.getId(),
            stadium.getName(),
            stadium.getAddress(),
            stadium.getTotalCapacity()
        );
    }
}
