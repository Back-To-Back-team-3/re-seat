package com.backtoback.reseat.domain.reservation.controller;

import com.backtoback.reseat.domain.reservation.dto.HoldTimeResponse;
import com.backtoback.reseat.domain.reservation.dto.ReservationCancelResponse;
import com.backtoback.reseat.domain.reservation.dto.ReservationResponse;
import com.backtoback.reseat.domain.reservation.dto.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.service.ReservationService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 좌석 선점(HOLD)·남은시간 조회·해제 API 컨트롤러.
 *
 * C-2-4 이번 사이클: 의도적 락 미적용 상태. 동시 요청 시 over-booking 발생 가능.
 * C-4에서 Redisson 분산락 도입 후 방어 예정.
 *
 * <p>인증: JWT (Bearer) 필수. Queue-Token 검증은 계약 확정 후 추가.
 * 컨트롤러는 얇게 유지 — 검증(@Valid) + 서비스 위임 + 응답 변환만.
 */
@Tag(name = "Reservation", description = "좌석 선점·남은시간 조회·해제 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    /** POST /api/v1/reservations */
    @Operation(
        summary = "좌석 선점 (HOLD)",
        description = """
                    대기열을 통과한 사용자가 최대 4석을 임시 선점합니다. 선점 유효 시간은 5분입니다.

                    ⚠️ C-2-4: 의도적 락 미적용. 동시성 제어 없음 → C-4에서 도입.
                    Queue-Token 검증: B-3 대기열 토큰 계약 확정 후 적용 예정.
                    """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "선점 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "INVALID_REQUEST / GAME_SEAT_NOT_IN_GAME",
            content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "GAME_NOT_FOUND / GAME_SEAT_NOT_FOUND",
            content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "SEAT_ALREADY_HELD — 이미 선점된 좌석",
            content = @Content)
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> holdSeats(
        @Parameter(description = "대기열 입장 토큰", example = "eyJ...")
        @RequestHeader(value = "Queue-Token", required = false) String queueToken,
        // TODO: B-3 대기열 토큰 검증 통합 — queueTokenValidator.validate(queueToken) 로 교체
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody SeatHoldRequest request
    ) {
        Long userId = userDetails.getUserId();
        ReservationResponse response = reservationService.holdSeats(userId, request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    /** GET /api/v1/reservations/{reservationId}/hold-time */
    @Operation(
        summary = "선점 남은 시간 조회",
        description = """
                    선점 만료까지 남은 시간(초)을 반환합니다. 이미 만료된 경우 remainingSeconds = 0.
                    410 Gone 처리는 C-3에서 추가 예정.
                    """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "RESERVATION_NOT_FOUND",
            content = @Content)
    })
    @GetMapping("/{reservationId}/hold-time")
    public ApiResponse<HoldTimeResponse> getHoldTime(
        @Parameter(description = "예약 ID", example = "1001")
        @PathVariable Long reservationId
    ) {
        HoldTimeResponse response = reservationService.getHoldTime(reservationId);
        return ApiResponse.success(response);
    }


    /**  */

}
