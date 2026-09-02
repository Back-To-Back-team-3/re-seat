package com.backtoback.reseat.domain.citydata.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.backtoback.reseat.domain.citydata.client.SeoulCityDataClient;
import com.backtoback.reseat.domain.citydata.client.dto.SeoulCityDataRawResponse;
import com.backtoback.reseat.domain.citydata.dto.response.StadiumCongestionResponse;
import com.backtoback.reseat.domain.citydata.exception.StadiumCongestionNotFoundException;
import com.backtoback.reseat.domain.citydata.model.StadiumCityArea;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StadiumCongestionService {

    private static final String CACHE_KEY_PREFIX = "citydata:stadium:";

    private final SeoulCityDataClient seoulCityDataClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${citydata.cache.ttl-minutes:10}")
    private long cacheTtlMinutes;

    // 구장 ID에 해당하는 실시간 혼잡도 정보 조회(Redis 캐시 우선 조회)
    public StadiumCongestionResponse getStadiumCongestion(Long stadiumNum) {
        // 지원 구장 매핑 확인
        StadiumCityArea cityArea
            = StadiumCityArea
                .findByStadiumNum(stadiumNum)
                .orElseThrow(() -> new StadiumCongestionNotFoundException(stadiumNum));

        String cacheKey = CACHE_KEY_PREFIX + stadiumNum;

        // redis 캐시 확인
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof StadiumCongestionResponse cachedResponse) {
                log.debug("구장 혼잡도 캐시 히트(stadiumNum = {})", stadiumNum);
                return cachedResponse;
            }
        } catch (Exception e) {
            log.warn("Reids 캐시 조회 중 오류 발생 (stadiumNum = {}), 외부 API를 직접 호출합니다", stadiumNum, e);
        }

        StadiumCongestionResponse response;
        try {
            // 외부 API 호출(캐시 미스)
            SeoulCityDataRawResponse rawResponse = seoulCityDataClient.fetchCityData(cityArea.getAreaName());
            response = mapToResponse(cityArea, rawResponse);
        } catch (Exception e) {
            log.warn("서울시 실시간 도시데이터 API 호출 실패 (stadiumNum = {}), 기본 혼잡도 데이터로 대체합니다: {}", stadiumNum, e.getMessage());
            response = createFallbackResponse(cityArea);
        }

        // Redis 캐시 저장
        try {
            redisTemplate.opsForValue().set(cacheKey, response, Duration.ofMinutes(cacheTtlMinutes));
        } catch (Exception e) {
            log.warn("Redis 캐시 저장 실패(stadiumNum = {})", stadiumNum, e);
        }
        return response;
    }

    private StadiumCongestionResponse createFallbackResponse(StadiumCityArea cityArea) {
        String nowStr
            = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return StadiumCongestionResponse
            .of(
                cityArea.getStadiumNum(),
                cityArea.getStadiumName(),
                cityArea.getAreaName(),
                "보통",
                "사람이 몰려있을 수 있지만 크게 붐비지 않으며, 도보 이동이 원활합니다.",
                14000,
                17000,
                cityArea.getLatitude(),
                cityArea.getLongitude(),
                nowStr
            );
    }

    private StadiumCongestionResponse mapToResponse(StadiumCityArea cityArea, SeoulCityDataRawResponse raw) {
        SeoulCityDataRawResponse.LivePopulationStatus status = raw.getCityData().getLivePopulationStatus().get(0);

        Integer minPopulation = parseIntegerSafely(status.getPpltnMin());
        Integer maxPopulation = parseIntegerSafely(status.getPpltnMax());

        return StadiumCongestionResponse
            .of(
                cityArea.getStadiumNum(),
                cityArea.getStadiumName(),
                cityArea.getAreaName(),
                status.getCongestionLevel(),
                status.getCongestionMessage(),
                minPopulation,
                maxPopulation,
                cityArea.getLatitude(),
                cityArea.getLongitude(),
                status.getPpltnTime()
            );
    }

    private Integer parseIntegerSafely(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
