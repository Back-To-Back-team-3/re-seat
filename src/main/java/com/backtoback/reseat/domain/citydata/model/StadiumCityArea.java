package com.backtoback.reseat.domain.citydata.model;

import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StadiumCityArea {
    JAMSIL(1L, "서울종합운동장 야구장", "잠실종합운동장", 37.5121, 127.0719);

    private final Long stadiumNum;
    private final String stadiumName;
    private final String areaName;
    private final Double latitude;
    private final Double longitude;

    // stadiumNum으로 서울시 도시데이터 구역 매핑 조회
    public static Optional<StadiumCityArea> findByStadiumNum(Long stadiumNum) {
        if (stadiumNum == null) {
            return Optional.empty();
        }
        return Arrays.stream(StadiumCityArea.values()).filter(area -> area.stadiumNum.equals(stadiumNum)).findFirst();
    }

    // 구장명으로 서울시 도시데이터 구역 매핑 조회
    public static Optional<StadiumCityArea> findByStadiumName(String stadiumName) {
        if (stadiumName == null || stadiumName.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(area -> area.stadiumName.equals(stadiumName)).findFirst();
    }
}
