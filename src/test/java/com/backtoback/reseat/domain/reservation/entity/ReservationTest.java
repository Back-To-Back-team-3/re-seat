package com.backtoback.reseat.domain.reservation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.backtoback.reseat.domain.reservation.exception.InvalidReservationStatusException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

@DisplayName("Reservation 상태 전이")
class ReservationTest {

    private Reservation reservationWith(ReservationStatus status) {
        return Reservation.builder()
            .reservationNo("RSV-TEST-000001")
            .holdExpiresAt(LocalDateTime.now().plusMinutes(10))
            .status(status)
            .build();
    }

    @Test
    @DisplayName("HOLDING 예약은 confirm()으로 CONFIRMED가 된다")
    void confirm_fromHolding_success() {
        Reservation reservation = reservationWith(ReservationStatus.HOLDING);

        reservation.confirm();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("HOLDING 예약은 cancel()로 CANCELED가 된다")
    void cancel_fromHolding_success() {
        Reservation reservation = reservationWith(ReservationStatus.HOLDING);

        reservation.cancel();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
    }

    @Test
    @DisplayName("HOLDING 예약은 expire()로 EXPIRED가 된다")
    void expire_fromHolding_success() {
        Reservation reservation = reservationWith(ReservationStatus.HOLDING);

        reservation.expire();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    @DisplayName("이미 CONFIRMED인 예약을 다시 cancel()하면 예외")
    void cancel_fromConfirmed_throws() {
        Reservation reservation = reservationWith(ReservationStatus.CONFIRMED);

        assertThatThrownBy(reservation::cancel)
            .isInstanceOf(InvalidReservationStatusException.class);
    }

    @Test
    @DisplayName("이미 CANCELED인 예약을 confirm()하면 예외")
    void confirm_fromCanceled_throws() {
        Reservation reservation = reservationWith(ReservationStatus.CANCELED);

        assertThatThrownBy(reservation::confirm)
            .isInstanceOf(InvalidReservationStatusException.class);
    }

    @Test
    @DisplayName("이미 EXPIRED인 예약을 expire()하면 예외 (재만료 불가)")
    void expire_fromExpired_throws() {
        Reservation reservation = reservationWith(ReservationStatus.EXPIRED);

        assertThatThrownBy(reservation::expire)
            .isInstanceOf(InvalidReservationStatusException.class);
    }
}
