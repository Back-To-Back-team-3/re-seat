package com.backtoback.reseat.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;

/**
 * HoldExpiryService 단위 테스트.
 * <p>
 * 스케줄 타이밍 비결정성을 배제하기 위해 HoldExpiryScheduler를 거치지 않고
 * releaseExpired()를 직접 호출해 검증한다.
 * <p>
 * 쿼리 실제 실행(SQL 레벨) 검증은 별도 Repository 테스트(Testcontainers)에서 수행한다.
 */
@ExtendWith(MockitoExtension.class)
class HoldExpiryServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private GameSeatRepository gameSeatRepository;

    @InjectMocks
    private HoldExpiryService holdExpiryService;

    /**
     * 시나리오 1: 만료 회수 정상 동작
     **/
    @Test
    @DisplayName("만료된 선점이 있을 때 예약은 EXPIRED로, 좌석은 AVAILABLE로 회수된다")
    void should_returnExpiredCountAndReleasedCount_when_expiredHoldsExist() {
        // given
        LocalDateTime now = LocalDateTime.now();

        given(reservationRepository.expireHoldingReservations(
            eq(now),
            eq(ReservationStatus.HOLDING),
            eq(ReservationStatus.EXPIRED))).willReturn(3);

        given(gameSeatRepository.releaseExpiredSeats(
            eq(now),
            eq(GameSeatStatus.HELD),
            eq(GameSeatStatus.AVAILABLE))).willReturn(3);

        // when
        HoldExpiryService.HoldExpiryResult result = holdExpiryService.releaseExpired(now);

        // then
        assertThat(result.expiredReservations()).isEqualTo(3);
        assertThat(result.releasedSeats()).isEqualTo(3);
        assertThat(result.total()).isEqualTo(6);
    }

    /**
     * 시나리오 2: 경합 회귀 — 연장된 좌석은 회수되지 않는다 (B5 오회수 방지)
     **/
    @Test
    @DisplayName("주문 생성으로 hold_expires_at이 연장된 좌석은 회수 대상에 포함되지 않는다")
    void should_notReleaseExtendedSeats_when_holdExpiresAtIsExtendedByOrderCreation() {
        // given
        // 주문 생성(#6.1)이 hold_expires_at을 미래로 연장한 상태를 시뮬레이션한다.
        // WHERE hold_expires_at < :now 조건이 거짓이 되어 UPDATE 영향 행이 0이 된다.
        LocalDateTime now = LocalDateTime.now();

        given(reservationRepository.expireHoldingReservations(
            eq(now),
            eq(ReservationStatus.HOLDING),
            eq(ReservationStatus.EXPIRED))).willReturn(0); // 연장으로 조건 불충족 → 회수 0건

        given(gameSeatRepository.releaseExpiredSeats(
            eq(now),
            eq(GameSeatStatus.HELD),
            eq(GameSeatStatus.AVAILABLE))).willReturn(0); // 연장으로 조건 불충족 → 회수 0건

        // when
        HoldExpiryService.HoldExpiryResult result = holdExpiryService.releaseExpired(now);

        // then — 정상 좌석이 오회수되지 않았음을 검증한다 (B5 좀비 HOLD 반대 케이스)
        assertThat(result.expiredReservations()).isZero();
        assertThat(result.releasedSeats()).isZero();
        assertThat(result.total()).isZero();
    }

    /**
     * 시나리오 3: 부분 회수 없음 — 예약·좌석이 항상 같은 트랜잭션에서 함께 전이된다
     **/
    @Test
    @DisplayName("예약 UPDATE와 좌석 UPDATE가 항상 함께 호출된다 — 부분 회수 없음")
    void should_callBothUpdatesAlways_when_releaseExpiredIsCalled() {
        // given
        LocalDateTime now = LocalDateTime.now();

        given(reservationRepository.expireHoldingReservations(
            eq(now),
            eq(ReservationStatus.HOLDING),
            eq(ReservationStatus.EXPIRED))).willReturn(2);

        given(gameSeatRepository.releaseExpiredSeats(
            eq(now),
            eq(GameSeatStatus.HELD),
            eq(GameSeatStatus.AVAILABLE))).willReturn(2);

        // when
        holdExpiryService.releaseExpired(now);

        // then — 예약·좌석 두 UPDATE 모두 반드시 호출됨을 검증한다
        then(reservationRepository).should().expireHoldingReservations(
            eq(now),
            eq(ReservationStatus.HOLDING),
            eq(ReservationStatus.EXPIRED));
        then(gameSeatRepository).should().releaseExpiredSeats(
            eq(now),
            eq(GameSeatStatus.HELD),
            eq(GameSeatStatus.AVAILABLE));
    }

    /**
     * 시나리오 4: UPDATE 순서 — reservations 먼저, game_seats 나중 (데드락 방지)
     **/
    @Test
    @DisplayName("예약 UPDATE가 좌석 UPDATE보다 먼저 호출된다 — 주문 생성 락 순서와 일치")
    void should_expireReservationsBeforeReleasingSeats_when_releaseExpiredIsCalled() {
        // given
        // 주문 생성이 예약 행을 먼저 비관적 락으로 잡은 뒤 좌석을 갱신하므로,
        // 스케줄러도 같은 순서로 갱신해야 데드락 가능성을 줄인다 (B3 원칙).
        LocalDateTime now = LocalDateTime.now();
        var inOrder = inOrder(reservationRepository, gameSeatRepository);

        given(reservationRepository.expireHoldingReservations(
            eq(now),
            eq(ReservationStatus.HOLDING),
            eq(ReservationStatus.EXPIRED))).willReturn(1);

        given(gameSeatRepository.releaseExpiredSeats(
            eq(now),
            eq(GameSeatStatus.HELD),
            eq(GameSeatStatus.AVAILABLE))).willReturn(1);

        // when
        holdExpiryService.releaseExpired(now);

        // then
        inOrder.verify(reservationRepository).expireHoldingReservations(
            eq(now),
            eq(ReservationStatus.HOLDING),
            eq(ReservationStatus.EXPIRED));
        inOrder.verify(gameSeatRepository).releaseExpiredSeats(
            eq(now),
            eq(GameSeatStatus.HELD),
            eq(GameSeatStatus.AVAILABLE));
    }
}
