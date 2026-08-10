package com.backtoback.reseat.domain.reservation.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 좌석 선점 응답 DTO.
 *
 * <p>POST /api/v1/reservations → 201 Created 시 반환.
 * 좌석은 gameSeats 배열로 좌석별 상태·가격을 노출한다.
 *
 * <p>합계(totalPrice)는 제공하지 않는다. 좌석 정가 합산·할인·수수료는
 * 주문(orders.total_amount) 단계(B)의 책임이며, 선점(C)은 좌석별 price 스냅샷까지만 책임진다.
 */
@Schema(description = "좌석 선점(HOLD) 응답")
public record ReservationResponse(

    @Schema(description = "예약 ID", example = "1001")
    Long reservationId,

    /** 업무번호. 화면 표기·고객 문의용. 예: RSV-20260711-A7B3C1 */
    @Schema(description = "예약 업무번호", example = "RSV-20260711-A7B3C1")
    String reservationNo,

    @Schema(description = "예약 상태", example = "HOLDING")
    ReservationStatus status,

    @Schema(description = "선점된 좌석 목록 (좌석별 상태·가격)")
    List<SeatHoldInfo> gameSeats,

    /** 선점 만료 시각 (now + 5분). 만료 스케줄러는 C-3에서 구현. */
    @Schema(description = "선점 만료 시각", example = "2026-07-11T18:37:00")
    LocalDateTime holdExpiresAt,

    @Schema(description = "경기 시각", example = "2026-07-11T18:30:00")
    LocalDateTime gameAt) {

    public static ReservationResponse from(Reservation reservation) {
        List<SeatHoldInfo> seats = reservation.getReservationSeats().stream()
            .map(SeatHoldInfo::from)
            .toList();

        return new ReservationResponse(
            reservation.getId(),
            reservation.getReservationNo(),
            reservation.getStatus(),
            seats,
            reservation.getHoldExpiresAt(),
            reservation.getGame().getGameAt());
    }

    /**
     * 좌석 단위 선점 정보. gameSeats 배열의 원소.
     */
    @Schema(description = "좌석별 선점 정보")
    public record SeatHoldInfo(

        @Schema(description = "경기 좌석 재고 ID (game_seats.id)", example = "5001")
        Long gameSeatId,

        @Schema(description = "좌석 상태", example = "HELD")
        GameSeatStatus status,

        @Schema(description = "선점 당시 가격 (price 스냅샷)", example = "18000")
        int price) {
        static SeatHoldInfo from(ReservationSeat rs) {
            GameSeat gs = rs.getGameSeat();
            return new SeatHoldInfo(gs.getId(), gs.getStatus(), rs.getPrice());
        }
    }
}
