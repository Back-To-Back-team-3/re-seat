package com.backtoback.reseat.domain.queue.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.queue.admin.dto.request.AdmissionMetricSearchCondition;
import com.backtoback.reseat.domain.queue.admin.dto.response.AdminQueueAdmissionMetricsResponse;
import com.backtoback.reseat.domain.queue.admin.dto.response.AdminQueueOverviewResponse;
import com.backtoback.reseat.domain.queue.admin.service.AdminQueueQueryService;
import com.backtoback.reseat.global.common.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 전용 경기별 대기열 현황과 입장 지표 조회 API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/queues")
public class AdminQueueController implements AdminQueueControllerDocs {

    private final AdminQueueQueryService adminQueueQueryService;

    /**
     * 경기별 현재 대기열 현황을 조회한다.
     *
     * @param gameId 조회할 경기 ID
     * @return 관리자 경기별 대기열 현황 응답
     */
    @Override
    @GetMapping("/games/{gameId}/overview")
    public ResponseEntity<ApiResponse<AdminQueueOverviewResponse>> getOverview(@PathVariable Long gameId) {

        return ResponseEntity.ok(ApiResponse.success("관리자 대기열 현황 조회 완료", adminQueueQueryService.getOverview(gameId)));
    }

    /**
     * 경기별 Queue-Token 발급 수를 요청한 기간 단위로 조회한다.
     *
     * @param gameId 조회할 경기 ID
     * @param condition 입장 지표 조회 조건
     * @return 관리자 경기별 입장 지표 응답
     */
    @Override
    @GetMapping("/games/{gameId}/admission-metrics")
    public ResponseEntity<ApiResponse<AdminQueueAdmissionMetricsResponse>> getAdmissionMetrics(
        @PathVariable Long gameId,
        AdmissionMetricSearchCondition condition
    ) {

        return ResponseEntity
            .ok(
                ApiResponse
                    .success("관리자 대기열 입장 지표 조회 완료", adminQueueQueryService.getAdmissionMetrics(gameId, condition))
            );
    }
}
