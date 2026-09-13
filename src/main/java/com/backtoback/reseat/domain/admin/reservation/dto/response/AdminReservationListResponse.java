package com.backtoback.reseat.domain.admin.reservation.dto.response;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;

import lombok.Builder;

// 관리자 예약·선점 목록 응답
@Builder
public record AdminReservationListResponse(
    Long reservationId,
    String reservationNo,
    Long userId,
    ReservationStatus status,
    Long remainingSeconds,
    // HOLDING만 값, 그 외 null
    List<AdminReservationSeatResponse> seats
) {
    public static AdminReservationListResponse of(
        Reservation reservation,
        List<ReservationSeat> seats,
        LocalDateTime now
    ) {
        Long remainingSeconds
            = reservation.getStatus() == ReservationStatus.HOLDING
                ? Math.max(0L, Duration.between(now, reservation.getHoldExpiresAt()).getSeconds()) : null;

        List<AdminReservationSeatResponse> seatResponses
            = seats.stream().map(AdminReservationSeatResponse::from).toList();

        return AdminReservationListResponse
            .builder()
            .reservationId(reservation.getId())
            .reservationNo(reservation.getReservationNo())
            .userId(reservation.getUser().getId())
            .status(reservation.getStatus())
            .remainingSeconds(remainingSeconds)
            .seats(seatResponses)
            .build();
    }
}
