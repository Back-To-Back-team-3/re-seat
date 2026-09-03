package com.backtoback.reseat.domain.citydata.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.backtoback.reseat.domain.citydata.client.SeoulCityDataClient;
import com.backtoback.reseat.domain.citydata.client.dto.SeoulCityDataRawResponse;
import com.backtoback.reseat.domain.citydata.dto.response.StadiumCongestionResponse;
import com.backtoback.reseat.domain.citydata.exception.StadiumCongestionNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("StadiumCongestionService 단위 테스트")
class StadiumCongestionServiceTest {

    @InjectMocks
    private StadiumCongestionService stadiumCongestionService;

    @Mock
    private SeoulCityDataClient seoulCityDataClient;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(stadiumCongestionService, "cacheTtlMinutes", 10L);
    }

    @Nested
    @DisplayName("구장 실시간 혼잡도 조회 (getStadiumCongestion)")
    class GetStadiumCongestionTest {

        @Test
        @DisplayName("Redis 캐시에 데이터가 존재하면 외부 API를 호출하지 않고 캐시 데이터를 반환한다")
        void should_returnCachedData_when_cacheHit() {
            // given
            Long stadiumNum = 1L;
            String cacheKey = "citydata:stadium:1";
            StadiumCongestionResponse cachedResponse
                = StadiumCongestionResponse
                    .of(
                        1L,
                        "서울종합운동장 야구장",
                        "잠실종합운동장",
                        "붐빔",
                        "혼잡합니다",
                        20000,
                        25000,
                        37.5121,
                        127.0719,
                        "2026-09-01 19:00"
                    );

            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(cacheKey)).willReturn(cachedResponse);

            // when
            StadiumCongestionResponse result = stadiumCongestionService.getStadiumCongestion(stadiumNum);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getStadiumNum()).isEqualTo(1L);
            assertThat(result.getCongestionLevel()).isEqualTo("붐빔");
            assertThat(result.getLatitude()).isEqualTo(37.5121);
            assertThat(result.getLongitude()).isEqualTo(127.0719);
            verify(seoulCityDataClient, never()).fetchCityData(any());
        }

        @Test
        @DisplayName("Redis 캐시에 데이터가 없으면 외부 API를 호출하고 결과를 Redis에 캐싱한 후 반환한다")
        void should_fetchFromApiAndCache_when_cacheMiss() {
            // given
            Long stadiumNum = 1L;
            String cacheKey = "citydata:stadium:1";

            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(cacheKey)).willReturn(null);

            SeoulCityDataRawResponse rawResponse
                = createMockRawResponse("잠실종합운동장", "약간 붐빔", "보통 수준입니다.", "15000", "20000", "2026-09-01 19:30");
            given(seoulCityDataClient.fetchCityData("잠실종합운동장")).willReturn(rawResponse);

            // when
            StadiumCongestionResponse result = stadiumCongestionService.getStadiumCongestion(stadiumNum);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getStadiumNum()).isEqualTo(1L);
            assertThat(result.getStadiumName()).isEqualTo("서울종합운동장 야구장");
            assertThat(result.getAreaName()).isEqualTo("잠실종합운동장");
            assertThat(result.getCongestionLevel()).isEqualTo("약간 붐빔");
            assertThat(result.getPopulationMin()).isEqualTo(15000);
            assertThat(result.getPopulationMax()).isEqualTo(20000);
            assertThat(result.getLatitude()).isEqualTo(37.5121);
            assertThat(result.getLongitude()).isEqualTo(127.0719);

            verify(seoulCityDataClient).fetchCityData("잠실종합운동장");
            verify(valueOperations)
                .set(eq(cacheKey), any(StadiumCongestionResponse.class), eq(Duration.ofMinutes(10L)));
        }

        @Test
        @DisplayName("지원하지 않는 구장 번호로 조회 시 StadiumCongestionNotFoundException이 발생한다")
        void should_throwException_when_unsupportedStadiumNum() {
            // given
            Long unsupportedStadiumNum = 999L;

            // when & then
            assertThatThrownBy(() -> stadiumCongestionService.getStadiumCongestion(unsupportedStadiumNum))
                .isInstanceOf(StadiumCongestionNotFoundException.class);
        }

        @Test
        @DisplayName("Redis 조회 중 예외가 발생해도 외부 API를 호출하여 정상적으로 결과를 반환한다 (장애 격리)")
        void should_fallbackToApi_when_redisFails() {
            // given
            Long stadiumNum = 1L;
            given(redisTemplate.opsForValue()).willThrow(new RuntimeException("Redis connection error"));

            SeoulCityDataRawResponse rawResponse
                = createMockRawResponse("잠실종합운동장", "여유", "쾌적합니다.", "5000", "8000", "2026-09-01 20:00");
            given(seoulCityDataClient.fetchCityData("잠실종합운동장")).willReturn(rawResponse);

            // when
            StadiumCongestionResponse result = stadiumCongestionService.getStadiumCongestion(stadiumNum);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getCongestionLevel()).isEqualTo("여유");
            verify(seoulCityDataClient).fetchCityData("잠실종합운동장");
        }
    }

    private SeoulCityDataRawResponse createMockRawResponse(
        String areaName,
        String level,
        String msg,
        String min,
        String max,
        String time
    ) {
        SeoulCityDataRawResponse raw = new SeoulCityDataRawResponse();
        SeoulCityDataRawResponse.CityData cityData = new SeoulCityDataRawResponse.CityData();
        ReflectionTestUtils.setField(cityData, "areaName", areaName);

        SeoulCityDataRawResponse.LivePopulationStatus status = new SeoulCityDataRawResponse.LivePopulationStatus();
        ReflectionTestUtils.setField(status, "congestionLevel", level);
        ReflectionTestUtils.setField(status, "congestionMessage", msg);
        ReflectionTestUtils.setField(status, "ppltnMin", min);
        ReflectionTestUtils.setField(status, "ppltnMax", max);
        ReflectionTestUtils.setField(status, "ppltnTime", time);

        ReflectionTestUtils.setField(cityData, "livePopulationStatus", List.of(status));
        ReflectionTestUtils.setField(raw, "cityData", cityData);

        return raw;
    }
}
