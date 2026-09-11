package com.backtoback.reseat.domain.queue.admin.controller;

import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.queue.admin.dto.request.AdmissionMetricSearchCondition;
import com.backtoback.reseat.domain.queue.admin.dto.response.AdminQueueAdmissionMetricsResponse;
import com.backtoback.reseat.domain.queue.admin.dto.response.AdminQueueOverviewResponse;
import com.backtoback.reseat.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 관리자 대기열 현황과 입장 지표 조회 API 명세.
 */
@Tag(
    name = "Admin - Queue",
    description = "관리자 대기열 현황 및 입장 지표 조회 API (ROLE_ADMIN 전용)"
)
@SecurityRequirement(name = "JWT Bearer Token")
public interface AdminQueueControllerDocs {

    @Operation(summary = "경기별 대기열 현황 조회")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "미인증",
            content = @Content
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "관리자 권한 없음",
            content = @Content
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "GAME_NOT_FOUND",
            content = @Content
        ),
    })
    ResponseEntity<ApiResponse<AdminQueueOverviewResponse>> getOverview(
        @Parameter(
            description = "경기 ID",
            example = "1",
            required = true
        ) Long gameId
    );

    @Operation(
        summary = "경기별 대기열 입장 지표 조회",
        description = "종료일을 포함해 최대 366일을 조회합니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 조회 기간",
            content = @Content
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "미인증",
            content = @Content
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "관리자 권한 없음",
            content = @Content
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "GAME_NOT_FOUND",
            content = @Content
        ),
    })
    ResponseEntity<ApiResponse<AdminQueueAdmissionMetricsResponse>> getAdmissionMetrics(
        @Parameter(
            description = "경기 ID",
            example = "1",
            required = true
        ) Long gameId,
        AdmissionMetricSearchCondition condition
    );
}
