package com.backtoback.reseat.domain.stadium.dto;

import com.backtoback.reseat.domain.stadium.entity.Stadium;

/**
 * 경기 응답에 포함되는 구장 요약 정보.
 *
 * <p>프론트가 구장 상세로 이동할 수 있도록 name과 함께 stadiumId를 노출한다.</p>
 */
public record StadiumSummaryResponse(Long stadiumId, String name) {

    public static StadiumSummaryResponse from(Stadium stadium) {
        return new StadiumSummaryResponse(stadium.getId(), stadium.getName());
    }
}
