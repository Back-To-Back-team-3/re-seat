package com.backtoback.reseat.domain.stadium.entity;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StadiumTest {

    @Test
    @DisplayName("좌표를 등록하면 위도와 경도가 함께 반영된다")
    void should_setBothCoordinates_when_registerCoordinatesCalled() {
        Stadium stadium = Stadium.of("서울종합운동장 야구장", "서울 송파구", 23750);
        BigDecimal latitude = new BigDecimal("37.5121676");
        BigDecimal longitude = new BigDecimal("127.0719084");

        stadium.registerCoordinates(latitude, longitude);

        assertThat(stadium.getLatitude()).isEqualByComparingTo(latitude);
        assertThat(stadium.getLongitude()).isEqualByComparingTo(longitude);
    }

    @Test
    @DisplayName("좌표를 등록하지 않은 구장은 위도·경도가 null이다")
    void should_haveNullCoordinates_when_notRegistered() {
        Stadium stadium = Stadium.of("서울종합운동장 야구장", "서울 송파구", 23750);

        assertThat(stadium.getLatitude()).isNull();
        assertThat(stadium.getLongitude()).isNull();
    }

    @Test
    @DisplayName("registerCoordinates를 다시 호출하면 좌표가 갱신된다")
    void should_overwriteCoordinates_when_registerCoordinatesCalledAgain() {
        Stadium stadium = Stadium.of("서울종합운동장 야구장", "서울 송파구", 23750);
        stadium.registerCoordinates(new BigDecimal("37.0000000"), new BigDecimal("127.0000000"));

        BigDecimal newLatitude = new BigDecimal("37.5121676");
        BigDecimal newLongitude = new BigDecimal("127.0719084");
        stadium.registerCoordinates(newLatitude, newLongitude);

        assertThat(stadium.getLatitude()).isEqualByComparingTo(newLatitude);
        assertThat(stadium.getLongitude()).isEqualByComparingTo(newLongitude);
    }
}
