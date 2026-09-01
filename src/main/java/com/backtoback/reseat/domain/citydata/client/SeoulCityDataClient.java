package com.backtoback.reseat.domain.citydata.client;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.backtoback.reseat.domain.citydata.client.dto.SeoulCityDataRawResponse;
import com.backtoback.reseat.domain.citydata.exception.CityDataApiException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class SeoulCityDataClient {

    private final WebClient webClient;

    @Value("${citydata.seoul.api-key:?}")
    private String apiKey;

    @Value("${citydata.seoul.base-url:?}")
    private String baseUrl;

    // 서울시 도시데이터 응답 데이터가 크기 때문에 인메모리 버퍼 사이즈를 2MB로 설정
    public SeoulCityDataClient() {
        this.webClient
            = WebClient
                .builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    /**
     * 서울시 실시간 도시데이터 API 호출해 구역별 원본 데이터 조회
     * URL 형식 :http://openapi.seoul.go.kr:8088/{KEY}/json/citydata/1/5/{AREA_NM}
     *
     * @param areaName 서울시 실시간 도시데이터 구역명(예: 잠실종합운동장)
     * @return 서울시 도시 데이터 원본 응답 DTO
     */
    public SeoulCityDataRawResponse fetchCityData(String areaName) {
        try {
            SeoulCityDataRawResponse response
                = webClient
                    .get()
                    .uri(baseUrl + "/{apiKey}/json/citydata/1/5/{areaName}", apiKey, areaName)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(
                        HttpStatusCode::isError,
                        clientResponse -> clientResponse
                            .bodyToMono(String.class)
                            .defaultIfEmpty("외부 API 에러 본문 없음")
                            .flatMap(
                                errorBody -> Mono
                                    .error(
                                        new CityDataApiException(
                                            "서울시 도시데이터 API 응답 오류 (상태코드: " + clientResponse.statusCode().value() + "에러: "
                                                + errorBody + ")"
                                        )
                                    )
                            )
                    )
                    .bodyToMono(SeoulCityDataRawResponse.class)
                    .block(Duration.ofSeconds(3));

            if (response == null || response.getCityData() == null
                || response.getCityData().getLivePopulationStatus() == null
                || response.getCityData().getLivePopulationStatus().isEmpty()) {
                throw new CityDataApiException("해당 지역(" + areaName + ")의 실시간 혼잡도 데이터가 존재하지 않습니다.");
            }
            return response;
        } catch (CityDataApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("서울시 도시데이터 API호출 중 오류 발생 (areaName={})", areaName, e);
            throw new CityDataApiException("서울시 도시데이터 API 통신 중 예외가 발생했습니다");
        }
    }
}
