package com.backtoback.reseat.domain.stadium.dto;

import java.math.BigDecimal;

import com.backtoback.reseat.domain.stadium.entity.Stadium;

/**
 * T3-14에서 소비하는 구장 위치 응답.
 *
 * @param stadiumId 구장 식별자
 * @param name 구장명
 * @param latitude 위도
 * @param longitude 경도
 */
public record StadiumLocationResponse(Long stadiumId, String name, BigDecimal latitude, BigDecimal longitude) {
    public static StadiumLocationResponse from(Stadium stadium) {
        return new StadiumLocationResponse(
            stadium.getId(),
            stadium.getName(),
            stadium.getLatitude(),
            stadium.getLongitude()
        );
    }
}
