package com.backtoback.reseat.domain.admin.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.backtoback.reseat.domain.admin.reservation.dto.response.AdminReservationListResponse;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.global.common.PageResponse;

/**
 * AdminReservationQueryService 단위 테스트.
 * <p>서비스가 리포지토리 조회 결과를 올바르게 조합·위임하는지를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminReservationQueryServiceTest {

    private static final Long GAME_ID = 1L;
    private static final Long RESERVATION_ID = 100L;

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationSeatRepository reservationSeatRepository;
    @Mock
    private GameRepository gameRepository;
    @Mock
    private Reservation reservation;
    @Mock
    private ReservationSeat reservationSeat;
    @Mock
    private GameSeat gameSeat;
    @Mock
    private Seat seat;
    @Mock
    private SeatZone zone;
    @Mock
    private User user;

    private AdminReservationQueryService adminReservationQueryService;

    @BeforeEach
    void setUp() {
        adminReservationQueryService
            = new AdminReservationQueryService(reservationRepository, reservationSeatRepository, gameRepository);
    }

    @DisplayName("존재하지 않는 gameId로 조회하면 GameNotFoundException을 던진다")
    @Test
    void getReservations_throwsGameNotFoundException_whenGameDoesNotExist() {
        given(gameRepository.existsById(GAME_ID)).willReturn(false);

        assertThatThrownBy(() -> adminReservationQueryService.getReservations(GAME_ID, null, PageRequest.of(0, 20)))
            .isInstanceOf(GameNotFoundException.class);

        // 존재 검증에서 이미 끝났으므로 예약 조회 자체는 시도하지 않아야 한다
        verify(reservationRepository, never()).findByGameAndStatus(any(), any(), any());
    }

    @DisplayName("HOLDING 예약은 holdExpiresAt 기준으로 remainingSeconds가 계산되고, 좌석 배치 조회 결과가 그룹핑된다")
    @Test
    void getReservations_calculatesRemainingSecondsAndGroupsSeats_whenStatusHolding() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Reservation> reservationPage = new PageImpl<>(List.of(reservation), pageable, 1);

        given(gameRepository.existsById(GAME_ID)).willReturn(true);
        given(reservationRepository.findByGameAndStatus(GAME_ID, ReservationStatus.HOLDING, pageable))
            .willReturn(reservationPage);
        given(reservation.getId()).willReturn(RESERVATION_ID);
        given(reservation.getReservationNo()).willReturn("RSV-TEST-HOLDING");
        given(reservation.getUser()).willReturn(user);
        given(user.getId()).willReturn(1L);
        given(reservation.getStatus()).willReturn(ReservationStatus.HOLDING);
        given(reservation.getHoldExpiresAt()).willReturn(LocalDateTime.now().plusSeconds(300));

        // AdminReservationSeatResponse.from()이 필요로 하는 체인 전체를 stub
        given(reservationSeat.getReservation()).willReturn(reservation);
        given(reservationSeat.getGameSeat()).willReturn(gameSeat);
        given(reservationSeat.getPrice()).willReturn(15_000);
        given(gameSeat.getId()).willReturn(3301L);
        given(gameSeat.getSeat()).willReturn(seat);
        given(seat.getZone()).willReturn(zone);
        given(seat.getSeatBlock()).willReturn("A");
        given(seat.getSeatRow()).willReturn("3");
        given(seat.getSeatNumber()).willReturn("12");
        given(zone.getName()).willReturn("1루 블루석");

        given(reservationSeatRepository.findWithGameSeatByReservationIdIn(List.of(RESERVATION_ID)))
            .willReturn(List.of(reservationSeat));

        PageResponse<AdminReservationListResponse> result
            = adminReservationQueryService.getReservations(GAME_ID, ReservationStatus.HOLDING, pageable);

        AdminReservationListResponse response = result.getContent().get(0);
        assertThat(response.seats()).hasSize(1);
        assertThat(response.seats().get(0).seat()).isEqualTo("1루 블루석 A-3-12");
        assertThat(response.remainingSeconds()).isBetween(290L, 300L);
    }

    @DisplayName("CONFIRMED/EXPIRED 예약은 remainingSeconds가 null이고, 좌석이 없어도 예외 없이 처리한다")
    @Test
    void getReservations_returnsNullRemainingSecondsAndEmptySeats_whenStatusNotHolding() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Reservation> reservationPage = new PageImpl<>(List.of(reservation), pageable, 1);

        given(gameRepository.existsById(GAME_ID)).willReturn(true);
        given(reservationRepository.findByGameAndStatus(GAME_ID, ReservationStatus.EXPIRED, pageable))
            .willReturn(reservationPage);
        given(reservation.getId()).willReturn(RESERVATION_ID);
        given(reservation.getReservationNo()).willReturn("RSV-TEST-EXPIRED");
        given(reservation.getUser()).willReturn(user);
        given(user.getId()).willReturn(1L);
        given(reservation.getStatus()).willReturn(ReservationStatus.EXPIRED);
        given(reservationSeatRepository.findWithGameSeatByReservationIdIn(List.of(RESERVATION_ID)))
            .willReturn(List.of());

        PageResponse<AdminReservationListResponse> result
            = adminReservationQueryService.getReservations(GAME_ID, ReservationStatus.EXPIRED, pageable);

        AdminReservationListResponse response = result.getContent().get(0);
        assertThat(response.seats()).isEmpty();
        assertThat(response.remainingSeconds()).isNull();
    }
}
