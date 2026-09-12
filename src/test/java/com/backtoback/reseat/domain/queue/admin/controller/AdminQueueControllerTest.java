package com.backtoback.reseat.domain.queue.admin.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.queue.admin.dto.request.AdmissionMetricSearchCondition;
import com.backtoback.reseat.domain.queue.admin.dto.response.AdminQueueAdmissionMetricResponse;
import com.backtoback.reseat.domain.queue.admin.dto.response.AdminQueueAdmissionMetricsResponse;
import com.backtoback.reseat.domain.queue.admin.dto.response.AdminQueueOverviewResponse;
import com.backtoback.reseat.domain.queue.admin.exception.QueueAdmissionMetricSearchConditionInvalidException;
import com.backtoback.reseat.domain.queue.admin.service.AdminQueueQueryService;
import com.backtoback.reseat.domain.queue.admin.service.AdmissionMetricPeriod;
import com.backtoback.reseat.global.exception.ErrorCode;

/**
 * 관리자 대기열 현황과 입장 지표 API 응답 및 인가를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AdminQueueController")
public class AdminQueueControllerTest {

    private static final Long GAME_ID = 1L;
    private static final LocalDateTime COLLECTED_AT = LocalDateTime.of(2026, 9, 11, 12, 0, 0);
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 1);
    private static final String OVERVIEW_URI = "/api/v1/admin/queues/games/{gameId}/overview";
    private static final String ADMISSION_METRICS_URI = "/api/v1/admin/queues/games/{gameId}/admission-metrics";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AdminQueueQueryService adminQueueQueryService;

    /**
     * 관리자 대기열 현황 API의 Service 응답 fixture를 생성한다.
     *
     * @return 경기별 대기열 현황 응답
     */
    private static AdminQueueOverviewResponse overviewResponse() {

        return new AdminQueueOverviewResponse(
            GAME_ID,
            BookingStatus.OPEN,
            12L,
            3L,
            7L,
            COLLECTED_AT
        );
    }

    /**
     * 관리자 입장 지표 API의 Service 응답 fixture를 생성한다.
     *
     * @return 일별 입장 지표 응답
     */
    private static AdminQueueAdmissionMetricsResponse metricsResponse() {

        return new AdminQueueAdmissionMetricsResponse(
            GAME_ID,
            AdmissionMetricPeriod.DAILY,
            FROM,
            TO,
            List.of(
                new AdminQueueAdmissionMetricResponse(FROM.toString(), 2L),
                new AdminQueueAdmissionMetricResponse(FROM.plusDays(1).toString(), 0L),
                new AdminQueueAdmissionMetricResponse(TO.toString(), 1L)
            )
        );
    }

    /**
     * 관리자 Queue API의 현황 및 입장 지표 요청을 제공한다.
     *
     * @return 인가를 검증할 관리자 Queue API 요청 Stream
     */
    private static Stream<MockHttpServletRequestBuilder> adminQueueRequests() {

        return Stream.of(
            get(OVERVIEW_URI, GAME_ID),
            get(ADMISSION_METRICS_URI, GAME_ID)
                .param("period", AdmissionMetricPeriod.DAILY.name())
                .param("from", FROM.toString())
                .param("to", TO.toString())
        );
    }

    // ---------- GET /games/{gameId}/overview ----------

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자가 경기별 대기열 현황을 조회하면 200과 현황을 반환한다.")
    void getOverview_withAdmin_returnsOk() throws Exception {

        // given
        AdminQueueOverviewResponse overviewResponse = overviewResponse();

        // Controller 테스트에서는 Redis와 Repository 집계를 반복하지 않고 Service 응답 계약만 사용한다.
        given(adminQueueQueryService.getOverview(GAME_ID))
            .willReturn(overviewResponse);

        // when
        ResultActions resultActions = mockMvc.perform(
            get(OVERVIEW_URI, GAME_ID)
        );

        // then
        // 응답의 모든 현황 값이 Service에서 받은 값 그대로 JSON으로 직렬화돼야 한다.
        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.gameId").value(GAME_ID))
            .andExpect(jsonPath("$.data.bookingStatus").value(BookingStatus.OPEN.name()))
            .andExpect(jsonPath("$.data.waitingCount").value(12L))
            .andExpect(jsonPath("$.data.usableAdmissionCount").value(3L))
            .andExpect(jsonPath("$.data.admittedToday").value(7L))
            .andExpect(jsonPath("$.data.collectedAt").value(
                COLLECTED_AT.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ));
    }

    // ---------- GET /games/{gameId}/admission-metrics ----------

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("관리자가 기간 조건으로 입장 지표를 조회하면 200과 기간별 발급 수를 반환한다.")
    void getAdmissionMetrics_withAdminAndCondition_returnsOk() throws Exception {

        // given
        AdminQueueAdmissionMetricsResponse metricsResponse = metricsResponse();

        given(adminQueueQueryService.getAdmissionMetrics(
            eq(GAME_ID),
            any(AdmissionMetricSearchCondition.class)
        )).willReturn(metricsResponse);

        // 요청의 period, from, to는 Controller가 AdmissionMetricSearchCondition으로 바인딩한다.
        ArgumentCaptor<AdmissionMetricSearchCondition> conditionCaptor = ArgumentCaptor
            .forClass(AdmissionMetricSearchCondition.class);

        // when
        ResultActions resultActions = mockMvc.perform(
            get(ADMISSION_METRICS_URI, GAME_ID)
                .param("period", AdmissionMetricPeriod.DAILY.name())
                .param("from", FROM.toString())
                .param("to", TO.toString())
        );

        // then
        // 날짜가 비어 있는 구간의 admittedCount=0도 응답 순서에 맞춰 검증한다.
        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.gameId").value(GAME_ID))
            .andExpect(jsonPath("$.data.period").value(AdmissionMetricPeriod.DAILY.name()))
            .andExpect(jsonPath("$.data.from").value(FROM.toString()))
            .andExpect(jsonPath("$.data.to").value(TO.toString()))
            .andExpect(jsonPath("$.data.series").isArray())
            .andExpect(jsonPath("$.data.series.length()").value(3L))
            .andExpect(jsonPath("$.data.series[0].bucket").value(FROM.toString()))
            .andExpect(jsonPath("$.data.series[0].admittedCount").value(2))
            .andExpect(jsonPath("$.data.series[1].bucket").value(FROM.plusDays(1).toString()))
            .andExpect(jsonPath("$.data.series[1].admittedCount").value(0))
            .andExpect(jsonPath("$.data.series[2].bucket").value(TO.toString()))
            .andExpect(jsonPath("$.data.series[2].admittedCount").value(1));

        then(adminQueueQueryService).should().getAdmissionMetrics(eq(GAME_ID), conditionCaptor.capture());

        // 요청 파라미터가 손실되거나 다른 날짜로 변환되지 않았는지 확인한다.
        AdmissionMetricSearchCondition capturedCondition = conditionCaptor.getValue();
        assertThat(capturedCondition.period()).isEqualTo(AdmissionMetricPeriod.DAILY);
        assertThat(capturedCondition.from()).isEqualTo(FROM);
        assertThat(capturedCondition.to()).isEqualTo(TO);
    }

    // ---------- 관리자 Queue API 오류 응답 ----------

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("잘못된 입장 지표 조회 조건이면 400을 반환한다.")
    void getAdmissionMetrics_withInvalidCondition_returnsBadRequest() throws Exception {

        // given
        QueueAdmissionMetricSearchConditionInvalidException invalidConditionException =
            new QueueAdmissionMetricSearchConditionInvalidException();

        // Controller까지 전달된 조회 조건 예외는 공통 예외 응답으로 변환된다.
        given(adminQueueQueryService.getAdmissionMetrics(eq(GAME_ID), any(AdmissionMetricSearchCondition.class)))
            .willThrow(invalidConditionException);

        // when
        ResultActions resultActions = mockMvc.perform(
            get(ADMISSION_METRICS_URI, GAME_ID)
                .param("period", AdmissionMetricPeriod.DAILY.name())
                .param("from", TO.toString())
                .param("to", FROM.toString())
        );

        // then
        // 잘못된 조회 기간은 서버 오류가 아니라 요청 오류로 응답해야 한다.
        resultActions
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.QUEUE_ADMISSION_METRIC_SEARCH_CONDITION_INVALID.getCode()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("존재하지 않는 경기의 대기열 현황을 조회하면 404를 반환한다.")
    void getOverview_withMissingGame_returnsNotFound() throws Exception {

        // given
        GameNotFoundException gameNotFoundException = new GameNotFoundException(GAME_ID);

        // 존재하지 않는 경기 예외는 공통 GAME_NOT_FOUND 응답으로 변환된다.
        given(adminQueueQueryService.getOverview(GAME_ID))
            .willThrow(gameNotFoundException);

        // when
        ResultActions resultActions = mockMvc.perform(
            get(OVERVIEW_URI, GAME_ID)
        );

        // then
        resultActions
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value(ErrorCode.GAME_NOT_FOUND.getCode()));
    }

    // ---------- 관리자 Queue API 인가 ----------

    @ParameterizedTest
    @MethodSource("adminQueueRequests")
    @WithMockUser(roles = "USER")
    @DisplayName("일반 사용자가 관리자 Queue API를 요청하면 403을 반환한다.")
    void requestAdminQueueApi_withUser_returnsForbidden(
        MockHttpServletRequestBuilder request
    ) throws Exception {

        // given
        // 같은 인가 정책을 사용하는 두 관리자 Queue 경로를 하나씩 검증한다.

        // when
        ResultActions resultActions = mockMvc.perform(
            request
        );

        // then
        // 인증됐더라도 ROLE_ADMIN이 없으면 Controller에 접근할 수 없다.
        resultActions
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("adminQueueRequests")
    @DisplayName("미인증 사용자가 관리자 Queue API를 요청하면 401을 반환한다.")
    void requestAdminQueueApi_withoutAuthentication_returnsUnauthorized(
        MockHttpServletRequestBuilder request
    ) throws Exception {

        // given
        // 별도의 인증 정보를 넣지 않아 anonymous 요청으로 실행한다.

        // when
        ResultActions resultActions = mockMvc.perform(
            request
        );

        // then
        // 미인증 요청은 권한 검사보다 먼저 인증 단계에서 거부돼야 한다.
        resultActions
            .andExpect(status().isUnauthorized());
    }
}
