package com.backtoback.reseat.domain.admin.reservation.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.admin.reservation.dto.response.AdminReservationListResponse;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

// 관리자 예약·선점 상태 관리 목록 API 명세
@Tag(
    name = "Admin - Reservation",
    description = "관리자 예약·선점 상태 관리 목록 조회 API (ROLE_ADMIN 전용, 조회 전용)"
)
@SecurityRequirement(name = "JWT Bearer Token")
public interface AdminReservationControllerDocs {

    @Operation(summary = "경기별 예약·선점 상태 목록 조회")
    @ApiResponses(
        {
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
        }
    )
    ResponseEntity<ApiResponse<PageResponse<AdminReservationListResponse>>> getReservations(
        @Parameter(
            description = "경기 ID",
            required = true
        ) Long gameId,
        @Parameter(description = "예약 상태 필터. 미지정 시 전체 조회") ReservationStatus status,
        Pageable pageable
    );
}
