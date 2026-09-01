package com.backtoback.reseat.domain.citydata.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeoulCityDataRawResponse {

    @JsonProperty("CITYDATA")
    private CityData cityData;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CityData {
        @JsonProperty("AREA_NM")
        private String areaName;

        @JsonProperty("LIVE_PPLTN_STTS")
        private List<LivePopulationStatus> livePopulationStatus;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LivePopulationStatus {
        @JsonProperty("AREA_CONGEST_LVL")
        private String congestionLevel; // 4단계(여유, 보통, 약간 붐빔, 붐빔)

        @JsonProperty("AREA_CONGEST_MSG")
        private String congestionMessage; // 혼잡도 상태 메시지

        @JsonProperty("AREA_PPLTN_MIN")
        private String ppltnMin; // 실시간 인구 최소치

        @JsonProperty("AREA_PPLTN_MAX")
        private String ppltnMax; // 실시간 인구 최대치

        @JsonProperty("PPLTN_TIME")
        private String ppltnTime; // 데이터 업데이트 기준 시각

    }
}
