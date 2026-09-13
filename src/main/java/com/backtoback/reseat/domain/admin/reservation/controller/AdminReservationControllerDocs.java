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

/**
 * 관리자 전용 예약·선점 상태 관리 목록 조회 API Swagger 문서화 인터페이스.
 * <p>ROLE_ADMIN 전용 API이므로 JWT Bearer Token @SecurityRequirement만 선언한다.
 * ADMIN 권한 검증은 Spring Security(SecurityConfig)가 처리한다.
 */
@Tag(
    name = "Admin - Reservation",
    description = """
        ROLE_ADMIN 필요. 경기 ID 기준으로 예약·선점 목록을 상태별로 조회한다.

        - status 미지정 시 전체 상태(HOLDING/CONFIRMED/EXPIRED)를 조회한다.
        - CANCELED는 이번 필터 옵션에 포함하지 않는다.
        - 기본 페이지 크기: 20
        - 기본 정렬: createdAt DESC
        - 허용 sort 필드: createdAt, holdExpiresAt
        - remainingSeconds는 status=HOLDING인 행에만 값이 채워지고, 그 외에는 null이다.
        - 예약 1건은 좌석을 최대 2개까지 가질 수 있어 seats는 배열로 반환된다.
        - 조회 전용 API이며 강제 해제·상태 변경 기능은 제공하지 않는다.
        """
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
