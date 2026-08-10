package com.backtoback.reseat.domain.reservation.controller;

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

import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.dto.response.HoldTimeResponse;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationCancelResponse;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationResponse;
import com.backtoback.reseat.domain.reservation.service.ReservationService;
import com.backtoback.reseat.domain.reservation.service.SeatHoldFacade;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 좌석 선점(HOLD)·남은시간 조회·해제 API 컨트롤러.
 *
 * <p>선점은 SeatHoldFacade를 통해 Queue-Token 검증 → 분산 락 → 트랜잭션 → 토큰 소비 흐름으로 처리한다.</p>
 * <p>컨트롤러는 얇게 유지 — 검증(@Valid) + 서비스 위임 + 응답 변환만.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservations")
public class ReservationController implements ReservationControllerDocs {

    private final SeatHoldFacade seatHoldFacade;
    private final ReservationService reservationService;

    /**
     * POST /api/v1/reservations
     */
    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> holdSeats(
        @RequestHeader(value = "Queue-Token", required = false)
        String queueToken,
        @AuthenticationPrincipal
        CustomUserDetails userDetails,
        @Valid @RequestBody
        SeatHoldRequest request) {
        ReservationResponse response = seatHoldFacade.holdSeats(userDetails.getId(), queueToken, request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("좌석 선점 성공", response));
    }

    /**
     * GET /api/v1/reservations/{reservationId}/hold-time
     */
    @Override
    @GetMapping("/{reservationId}/hold-time")
    public ResponseEntity<ApiResponse<HoldTimeResponse>> getHoldTime(
        @PathVariable
        Long reservationId,
        @AuthenticationPrincipal
        CustomUserDetails userDetails) {
        HoldTimeResponse response = reservationService.getHoldTime(reservationId, userDetails.getId());

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("선점 잔여 시간 조회 성공", response));
    }

    /**
     * DELETE /api/v1/reservations/{reservationId}
     */
    @Override
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationCancelResponse>> releaseHold(
        @PathVariable
        Long reservationId,
        @AuthenticationPrincipal
        CustomUserDetails userDetails) {
        ReservationCancelResponse response = reservationService.releaseHold(reservationId, userDetails.getId());

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("좌석 선점 해제 성공", response));
    }
}
