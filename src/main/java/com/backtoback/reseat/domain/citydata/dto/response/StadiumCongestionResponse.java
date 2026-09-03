package com.backtoback.reseat.domain.citydata.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StadiumCongestionResponse {

    private Long stadiumNum;
    private String stadiumName;
    private String areaName;
    private String congestionLevel;
    private String congestionMessage;
    private Integer populationMin;
    private Integer populationMax;
    private Double latitude;
    private Double longitude;
    private String observedAt;

    public static StadiumCongestionResponse of(
        Long stadiumNum,
        String stadiumName,
        String areaName,
        String congestionLevel,
        String congestionMessage,
        Integer populationMin,
        Integer populationMax,
        Double latitude,
        Double longitude,
        String observedAt
    ) {
        return StadiumCongestionResponse
            .builder()
            .stadiumNum(stadiumNum)
            .stadiumName(stadiumName)
            .areaName(areaName)
            .congestionLevel(congestionLevel)
            .congestionMessage(congestionMessage)
            .populationMin(populationMin)
            .populationMax(populationMax)
            .latitude(latitude)
            .longitude(longitude)
            .observedAt(observedAt)
            .build();
    }
}
