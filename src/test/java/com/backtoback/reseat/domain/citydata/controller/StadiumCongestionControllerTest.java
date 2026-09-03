package com.backtoback.reseat.domain.citydata.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.backtoback.reseat.domain.citydata.dto.response.StadiumCongestionResponse;
import com.backtoback.reseat.domain.citydata.exception.CityDataApiException;
import com.backtoback.reseat.domain.citydata.exception.StadiumCongestionNotFoundException;
import com.backtoback.reseat.domain.citydata.service.StadiumCongestionService;
import com.backtoback.reseat.global.exception.GlobalExceptionHandler;

@WebMvcTest(StadiumCongestionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("StadiumCongestionController 슬라이스 테스트")
class StadiumCongestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StadiumCongestionService stadiumCongestionService;

    @Nested
    @DisplayName("구장 실시간 혼잡도 조회 API (GET /api/v1/congestion/stadiums/{stadiumNum})")
    class GetStadiumCongestionApiTest {

        @Test
        @DisplayName("존재하는 구장 번호로 요청 시 200 OK와 혼잡도 데이터를 반환한다")
        void should_return200_when_stadiumExists() throws Exception {
            // given
            Long stadiumNum = 1L;
            StadiumCongestionResponse response
                = StadiumCongestionResponse
                    .of(
                        1L,
                        "서울종합운동장 야구장",
                        "잠실종합운동장",
                        "붐빔",
                        "사람들이 몰려있어 혼잡합니다.",
                        24000,
                        26000,
                        37.5121,
                        127.0719,
                        "2026-09-01 19:30"
                    );

            given(stadiumCongestionService.getStadiumCongestion(stadiumNum)).willReturn(response);

            // when & then
            mockMvc
                .perform(get("/api/v1/congestion/stadiums/{stadiumNum}", stadiumNum))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("구장 실시간 혼잡도 조회 성공"))
                .andExpect(jsonPath("$.data.stadiumNum").value(1))
                .andExpect(jsonPath("$.data.stadiumName").value("서울종합운동장 야구장"))
                .andExpect(jsonPath("$.data.areaName").value("잠실종합운동장"))
                .andExpect(jsonPath("$.data.congestionLevel").value("붐빔"))
                .andExpect(jsonPath("$.data.latitude").value(37.5121))
                .andExpect(jsonPath("$.data.longitude").value(127.0719))
                .andExpect(jsonPath("$.data.populationMin").value(24000))
                .andExpect(jsonPath("$.data.populationMax").value(26000));
        }

        @Test
        @DisplayName("미지원 또는 존재하지 않는 구장 번호로 요청 시 404 NOT_FOUND를 반환한다")
        void should_return404_when_stadiumNotFound() throws Exception {
            // given
            Long invalidStadiumNum = 999L;
            given(stadiumCongestionService.getStadiumCongestion(invalidStadiumNum))
                .willThrow(new StadiumCongestionNotFoundException(invalidStadiumNum));

            // when & then
            mockMvc
                .perform(get("/api/v1/congestion/stadiums/{stadiumNum}", invalidStadiumNum))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("STADIUM_NOT_FOUND"));
        }

        @Test
        @DisplayName("외부 API 연동 실패 시 502 BAD_GATEWAY를 반환한다")
        void should_return502_when_externalApiFails() throws Exception {
            // given
            Long stadiumNum = 1L;
            given(stadiumCongestionService.getStadiumCongestion(stadiumNum))
                .willThrow(new CityDataApiException("서울시 도시데이터 API 통신 중 예외가 발생했습니다"));

            // when & then
            mockMvc
                .perform(get("/api/v1/congestion/stadiums/{stadiumNum}", stadiumNum))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("EXTERNAL_API_ERROR"));
        }
    }
}
