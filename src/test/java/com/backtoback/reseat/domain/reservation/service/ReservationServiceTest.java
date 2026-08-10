package com.backtoback.reseat.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.exception.ReservationAccessDeniedException;
import com.backtoback.reseat.domain.reservation.exception.ReservationNotFoundException;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.user.entity.User;

/**
 * ReservationService 단위 테스트.
 * <p>
 * 소유자 검증(verifyOwner) 로직에 집중한다.
 * 서비스 레이어의 행 단위 소유권 가드가 올바르게 동작하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

	@Mock
	private ReservationRepository reservationRepository;

	@InjectMocks
	private ReservationService reservationService;

	/**
	 * getHoldTime — 소유자 검증
	 **/

	@Test
	@DisplayName("본인 예약의 남은 시간을 조회하면 정상 응답한다")
	void should_returnHoldTime_when_requesterIsOwner() {
		// given
		Long reservationId = 1L;
		Long ownerId = 100L;

		User owner = mock(User.class);
		given(owner.getId()).willReturn(ownerId);

		Reservation reservation = mock(Reservation.class);
		given(reservation.getUser()).willReturn(owner);
		given(reservation.getHoldExpiresAt()).willReturn(LocalDateTime.now().plusMinutes(5));

		given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

		// when & then — 예외 없이 정상 동작
		assertThatCode(() -> reservationService.getHoldTime(reservationId, ownerId))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("타인 예약의 남은 시간을 조회하면 RESERVATION_ACCESS_DENIED(403)가 발생한다")
	void should_throwReservationAccessDeniedException_when_requesterIsNotOwnerOnGetHoldTime() {
		// given
		Long reservationId = 1L;
		Long ownerId = 100L;
		Long intruderId = 999L;

		User owner = mock(User.class);
		given(owner.getId()).willReturn(ownerId);

		Reservation reservation = mock(Reservation.class);
		given(reservation.getUser()).willReturn(owner);

		given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

		// when & then
		assertThatThrownBy(() -> reservationService.getHoldTime(reservationId, intruderId))
			.isInstanceOf(ReservationAccessDeniedException.class);
	}

	/**
	 * releaseHold — 소유자 검증
	 **/

	@Test
	@DisplayName("타인 예약을 해제하면 RESERVATION_ACCESS_DENIED(403)가 발생한다")
	void should_throwReservationAccessDeniedException_when_requesterIsNotOwnerOnReleaseHold() {
		// given
		Long reservationId = 1L;
		Long ownerId = 100L;
		Long intruderId = 999L;

		User owner = mock(User.class);
		given(owner.getId()).willReturn(ownerId);

		Reservation reservation = mock(Reservation.class);
		given(reservation.getUser()).willReturn(owner);

		given(reservationRepository.findWithSeatsById(reservationId))
			.willReturn(Optional.of(reservation));

		// when & then
		assertThatThrownBy(() -> reservationService.releaseHold(reservationId, intruderId))
			.isInstanceOf(ReservationAccessDeniedException.class);
	}

	/**
	 * 공통 — 존재하지 않는 예약
	 **/

	@Test
	@DisplayName("존재하지 않는 예약 ID로 조회하면 ReservationNotFoundException이 발생한다 — 소유권 검증 전 처리")
	void should_throwReservationNotFoundException_when_reservationNotFound() {
		// given
		Long nonExistentId = 999L;
		Long requesterId = 100L;

		given(reservationRepository.findById(nonExistentId)).willReturn(Optional.empty());

		// when & then — 소유권 검증(403)이 아니라 존재 검증(404)에서 먼저 터짐
		assertThatThrownBy(() -> reservationService.getHoldTime(nonExistentId, requesterId))
			.isInstanceOf(ReservationNotFoundException.class);
	}
}
