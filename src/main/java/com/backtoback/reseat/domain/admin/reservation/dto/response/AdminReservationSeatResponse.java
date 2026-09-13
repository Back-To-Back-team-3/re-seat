package com.backtoback.reseat.domain.admin.reservation.dto.response;

import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;

import lombok.Builder;

// 예약에 포함된 좌석 1건의 관리자 응답
// 예약은 좌석을 최대 2개까지 가질 수 있다.
@Builder
public record AdminReservationSeatResponse(
    Long gameSeatId,
    String seat, // "{구역명} {블록}-{열}-{번호}"
    int price
) {
    public static AdminReservationSeatResponse from(ReservationSeat reservationSeat) {
        var gameSeat = reservationSeat.getGameSeat();
        var seatEntity = gameSeat.getSeat();
        var zone = seatEntity.getZone();

        String formattedSeat
            = String
                .format(
                    "%s %s-%s-%s",
                    zone != null ? zone.getName() : "",
                    seatEntity.getSeatBlock(),
                    seatEntity.getSeatRow(),
                    seatEntity.getSeatNumber()
                )
                .trim();

        return AdminReservationSeatResponse
            .builder()
            .gameSeatId(gameSeat.getId())
            .seat(formattedSeat)
            .price(reservationSeat.getPrice()) // 선점 시점 가격 스냅샷
            .build();
    }
}
