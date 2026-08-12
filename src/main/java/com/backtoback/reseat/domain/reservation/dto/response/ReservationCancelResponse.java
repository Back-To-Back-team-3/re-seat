package com.backtoback.reseat.domain.reservation.dto.response;

import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 선점 해제 응답 DTO.
 * <p>DELETE /api/v1/reservations/{id} → 200 OK 시 반환.
 * 취소 결과 상태를 바디로 확인시켜 클라이언트 상태 동기화를 돕는다.
 */
@Schema(description = "선점 해제 응답")
public record ReservationCancelResponse(

    @Schema(
        description = "예약 ID",
        example = "1001"
    ) Long reservationId,

    @Schema(
        description = "예약 상태",
        example = "CANCELED"
    ) ReservationStatus status
) {
	public static ReservationCancelResponse from(Reservation reservation) {
		return new ReservationCancelResponse(reservation.getId(), reservation.getStatus());
	}
}
