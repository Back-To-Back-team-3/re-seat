package com.backtoback.reseat.domain.reservation.dto.response;

import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 선점 남은 시간 응답 DTO.
 *
 * <p>프론트의 카운트다운 표시를 위한 경량 응답.
 * 만료된 경우 remainingSeconds = 0 반환 (음수 방지).
 * 410 Gone 처리는 C-3에서 추가.
 */
@Schema(description = "선점 남은 시간 응답")
public record HoldTimeResponse(

    @Schema(description = "예약 ID", example = "1001")
    Long reservationId,

    /** 남은 초. 만료 시 0. */
    @Schema(description = "선점 만료까지 남은 초", example = "230")
    long remainingSeconds,

    @Schema(description = "예약 상태", example = "HOLDING")
    ReservationStatus status,

    /** 선점 만료 절대 시각 (클라이언트 시계 동기화용, 명세서 대비 추가 필드) */
    @Schema(description = "선점 만료 시각", example = "2026-07-11T18:37:00")
    LocalDateTime expiresAt
) {
    public static HoldTimeResponse from(Reservation reservation) {
        long remaining = Duration.between(
            LocalDateTime.now(),
            reservation.getHoldExpiresAt()
        ).getSeconds();

        return new HoldTimeResponse(
            reservation.getId(),
            Math.max(0L, remaining),   // 음수 방지
            reservation.getStatus(),
            reservation.getHoldExpiresAt()
        );
    }
}
