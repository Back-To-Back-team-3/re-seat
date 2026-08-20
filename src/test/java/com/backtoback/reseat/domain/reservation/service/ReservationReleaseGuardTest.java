package com.backtoback.reseat.domain.reservation.service;

import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationCancelResponse;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.exception.PreReservationExpiredException;
import com.backtoback.reseat.domain.reservation.exception.ReservationAccessDeniedException;
import com.backtoback.reseat.domain.reservation.exception.ReservationNotFoundException;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Disabled("테스트 제외")
@ExtendWith(MockitoExtension.class)
class ReservationReleaseGuardTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long RESERVATION_ID = 1001L;

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private GameSeatRepository gameSeatRepository;
    @Mock
    private GameRepository gameRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReservationNumberGenerator reservationNumberGenerator;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService
            = new ReservationService(
                reservationRepository,
                gameSeatRepository,
                gameRepository,
                userRepository,
                reservationNumberGenerator
            );
    }

    private User ownerUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(OWNER_ID);
        return user;
    }

    @Test
    @DisplayName("만료 시각이 지난 HOLDING 예약(스케줄러 미회수 구간)을 해제하면 410이 반환된다")
    void should_throwPreReservationExpired_when_holdExpiresAtPassed() {
        Reservation reservation
            = Reservation
                .builder()
                .user(ownerUser())
                .status(ReservationStatus.HOLDING)
                .holdExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(reservationRepository.findWithSeatsById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.releaseHold(RESERVATION_ID, OWNER_ID))
            .isInstanceOf(PreReservationExpiredException.class);
    }

    @Test
    @DisplayName("이미 취소된 예약에 대한 재취소 요청은 200으로 멱등 처리된다")
    void should_returnCurrentState_when_reservationAlreadyCanceled() {
        Reservation reservation
            = Reservation
                .builder()
                .user(ownerUser())
                .status(ReservationStatus.CANCELED)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        when(reservationRepository.findWithSeatsById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

        ReservationCancelResponse response = reservationService.releaseHold(RESERVATION_ID, OWNER_ID);

        assertThat(response.status()).isEqualTo(ReservationStatus.CANCELED);
    }

    @Test
    @DisplayName("타인의 예약을 해제하려 하면 만료·취소 상태와 무관하게 403이 우선 반환된다")
    void should_throwAccessDenied_when_requesterIsNotOwner() {
        Reservation reservation
            = Reservation
                .builder()
                .user(ownerUser())
                .status(ReservationStatus.HOLDING)
                .holdExpiresAt(LocalDateTime.now().minusMinutes(1)) // 만료 상태이지만 소유권 가드가 먼저 걸려야 함
                .build();
        when(reservationRepository.findWithSeatsById(RESERVATION_ID)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.releaseHold(RESERVATION_ID, OTHER_USER_ID))
            .isInstanceOf(ReservationAccessDeniedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 예약 해제 요청은 404를 반환한다")
    void should_throwReservationNotFound_when_reservationDoesNotExist() {
        when(reservationRepository.findWithSeatsById(RESERVATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.releaseHold(RESERVATION_ID, OWNER_ID))
            .isInstanceOf(ReservationNotFoundException.class);
    }
}
