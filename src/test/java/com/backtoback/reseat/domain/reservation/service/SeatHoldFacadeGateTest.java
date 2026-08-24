package com.backtoback.reseat.domain.reservation.service;

import com.backtoback.reseat.domain.queue.service.AdmissionTokenService;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationResponse;
import com.backtoback.reseat.domain.reservation.exception.MaxSeatCountExceededException;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.domain.reservation.service.lock.SeatLockStrategy;
import com.backtoback.reseat.domain.reservation.service.lock.UserGameLockStrategy;
import com.backtoback.reseat.domain.reservation.service.port.TicketCountPort;
import com.backtoback.reseat.domain.reservation.service.port.UserVerificationPort;
import com.backtoback.reseat.domain.user.exception.UserNotVerifiedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SeatHoldFacade 수량·토큰 게이트 통합 테스트.
 */
@Disabled("테스트 제외")
@ExtendWith(MockitoExtension.class)
class SeatHoldFacadeGateTest {

    private static final Long USER_ID = 1L;
    private static final Long GAME_ID = 10L;
    private static final String TOKEN = "qt_test-token";

    @Mock
    private ReservationService reservationService;
    @Mock
    private SeatLockStrategy seatLockStrategy;
    @Mock
    private AdmissionTokenService admissionTokenService;
    @Mock
    private ReservationSeatRepository reservationSeatRepository;
    @Mock
    private TicketCountPort ticketCountPort;
    @Mock
    private UserVerificationPort userVerificationPort;
    @Mock
    private UserGameLockStrategy userGameLockStrategy;

    private SeatHoldFacade seatHoldFacade;

    @BeforeEach
    void setUp() {
        seatHoldFacade
            = new SeatHoldFacade(
                reservationService,
                seatLockStrategy,
                admissionTokenService,
                reservationSeatRepository,
                ticketCountPort,
                userVerificationPort,
                userGameLockStrategy
            );
    }

    /** executeWithLocks가 실제 락 없이 action을 즉시 실행하도록 스텁한다. */
    @SuppressWarnings("unchecked")
    private void stubLockPassthrough() {
        when(seatLockStrategy.executeWithLocks(anyList(), any(Supplier.class)))
            .thenAnswer(invocation -> ((Supplier<ReservationResponse>)invocation.getArgument(1)).get());
    }

    /** executeWithLock(사용자·경기 락)이 실제 락 없이 action을 즉시 실행하도록 스텁한다. */
    @SuppressWarnings("unchecked")
    private void stubUserGameLockPassthrough() {
        when(userGameLockStrategy.executeWithLock(anyLong(), anyLong(), any(Supplier.class)))
            .thenAnswer(invocation -> ((Supplier<ReservationResponse>)invocation.getArgument(2)).get());
    }

    @Test
    @DisplayName("미인증 사용자가 만료된 토큰으로 요청해도 403이 우선 반환된다")
    void should_throw403First_when_userNotVerifiedAndTokenExpired() {
        // given
        SeatHoldRequest request = new SeatHoldRequest(GAME_ID, List.of(101L));
        when(userVerificationPort.isVerified(USER_ID)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> seatHoldFacade.holdSeats(USER_ID, TOKEN, request))
            .isInstanceOf(UserNotVerifiedException.class);

        // 본인인증 게이트에서 이미 차단됐으므로 토큰 검증·사용자 락 모두 호출되지 않아야 한다.
        verifyNoInteractions(admissionTokenService, userGameLockStrategy);
    }

    @Test
    @DisplayName("보유 1좌석 + 1좌석 요청은 상한 내이므로 선점이 진행된다")
    void should_proceedToLock_when_heldOneAndRequestedOneWithinLimit() {
        // given
        SeatHoldRequest request = new SeatHoldRequest(GAME_ID, List.of(101L));
        when(userVerificationPort.isVerified(USER_ID)).thenReturn(true);
        stubUserGameLockPassthrough();
        when(reservationSeatRepository.countActiveHoldingSeats(eq(USER_ID), eq(GAME_ID), any(LocalDateTime.class)))
            .thenReturn(1);
        when(ticketCountPort.countActiveTickets(USER_ID, GAME_ID)).thenReturn(0);
        stubLockPassthrough();
        when(reservationService.holdSeats(eq(USER_ID), eq(request))).thenReturn(mock(ReservationResponse.class));

        // when
        seatHoldFacade.holdSeats(USER_ID, TOKEN, request);

        // then
        verify(reservationService).holdSeats(USER_ID, request);
    }

    @Test
    @DisplayName("보유 1좌석 + 2좌석 요청은 상한을 초과해 MAX_SEAT_COUNT_EXCEEDED가 발생하고 락을 획득하지 않는다")
    void should_throwMaxSeatCountExceeded_when_heldOneAndRequestedTwo() {
        // given
        SeatHoldRequest request = new SeatHoldRequest(GAME_ID, List.of(101L, 102L));
        when(userVerificationPort.isVerified(USER_ID)).thenReturn(true);
        stubUserGameLockPassthrough();
        when(reservationSeatRepository.countActiveHoldingSeats(eq(USER_ID), eq(GAME_ID), any(LocalDateTime.class)))
            .thenReturn(1);
        when(ticketCountPort.countActiveTickets(USER_ID, GAME_ID)).thenReturn(0);

        // when & then
        assertThatThrownBy(() -> seatHoldFacade.holdSeats(USER_ID, TOKEN, request))
            .isInstanceOf(MaxSeatCountExceededException.class);

        // 수량 게이트에서 차단되면 락 획득 단계까지 가면 안 된다
        verifyNoInteractions(seatLockStrategy);
    }

    @Test
    @DisplayName("보유 2좌석 + 1좌석 요청은 상한을 초과해 MAX_SEAT_COUNT_EXCEEDED가 발생한다")
    void should_throwMaxSeatCountExceeded_when_heldTwoAndRequestedOne() {
        // given
        SeatHoldRequest request = new SeatHoldRequest(GAME_ID, List.of(101L));
        when(userVerificationPort.isVerified(USER_ID)).thenReturn(true);
        stubUserGameLockPassthrough();
        when(reservationSeatRepository.countActiveHoldingSeats(eq(USER_ID), eq(GAME_ID), any(LocalDateTime.class)))
            .thenReturn(2);
        when(ticketCountPort.countActiveTickets(USER_ID, GAME_ID)).thenReturn(0);

        // when & then
        assertThatThrownBy(() -> seatHoldFacade.holdSeats(USER_ID, TOKEN, request))
            .isInstanceOf(MaxSeatCountExceededException.class);
    }

    @Test
    @DisplayName("HOLDING 좌석 0개 + 유효 티켓 2장 상태에서 1좌석 요청은 상한을 초과해 차단된다")
    void should_throwMaxSeatCountExceeded_when_activeTicketCountAloneExceedsLimit() {
        // given
        SeatHoldRequest request = new SeatHoldRequest(GAME_ID, List.of(101L));
        when(userVerificationPort.isVerified(USER_ID)).thenReturn(true);
        stubUserGameLockPassthrough();
        when(reservationSeatRepository.countActiveHoldingSeats(eq(USER_ID), eq(GAME_ID), any(LocalDateTime.class)))
            .thenReturn(0);
        when(ticketCountPort.countActiveTickets(USER_ID, GAME_ID)).thenReturn(2);

        // when & then
        assertThatThrownBy(() -> seatHoldFacade.holdSeats(USER_ID, TOKEN, request))
            .isInstanceOf(MaxSeatCountExceededException.class);
    }

    @Test
    @DisplayName("검증 순서는 토큰 → 수량이다: 토큰이 유효하지 않으면 수량 검증을 시도하지 않는다")
    void should_notCheckQuantity_when_tokenValidationFails() {
        // given
        SeatHoldRequest request = new SeatHoldRequest(GAME_ID, List.of(101L));
        when(userVerificationPort.isVerified(USER_ID)).thenReturn(true);
        doThrow(new RuntimeException("token invalid"))
            .when(admissionTokenService)
            .validateToken(USER_ID, GAME_ID, TOKEN);

        // when & then
        assertThatThrownBy(() -> seatHoldFacade.holdSeats(USER_ID, TOKEN, request))
            .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(reservationSeatRepository, ticketCountPort, seatLockStrategy);
    }

    @Test
    @DisplayName("1좌석씩 반복 선점 — 두 번째 요청 시점의 누적 보유 수가 상한을 초과하면 차단된다")
    void should_throwMaxSeatCountExceeded_when_repeatedSingleSeatHoldsExceedLimit() {
        // given: 이미 1좌석을 보유한 상태에서(첫 요청 이후) 다시 1좌석 요청 → 누적 2좌석까지는 통과해야 함
        SeatHoldRequest secondRequest = new SeatHoldRequest(GAME_ID, List.of(102L));
        when(userVerificationPort.isVerified(USER_ID)).thenReturn(true);
        stubUserGameLockPassthrough();
        when(reservationSeatRepository.countActiveHoldingSeats(eq(USER_ID), eq(GAME_ID), any(LocalDateTime.class)))
            .thenReturn(1);
        when(ticketCountPort.countActiveTickets(USER_ID, GAME_ID)).thenReturn(0);
        stubLockPassthrough();
        when(reservationService.holdSeats(eq(USER_ID), eq(secondRequest))).thenReturn(mock(ReservationResponse.class));

        // when: 두 번째 1좌석 요청 (누적 1 + 1 = 2, 상한 이내 → 통과)
        seatHoldFacade.holdSeats(USER_ID, TOKEN, secondRequest);

        // then
        verify(reservationService).holdSeats(USER_ID, secondRequest);

        // given: 세 번째 1좌석 요청 시점엔 이미 2좌석 보유 (누적 2 + 1 = 3 → 차단)
        SeatHoldRequest thirdRequest = new SeatHoldRequest(GAME_ID, List.of(103L));
        when(reservationSeatRepository.countActiveHoldingSeats(eq(USER_ID), eq(GAME_ID), any(LocalDateTime.class)))
            .thenReturn(2);

        // when & then
        assertThatThrownBy(() -> seatHoldFacade.holdSeats(USER_ID, TOKEN, thirdRequest))
            .isInstanceOf(MaxSeatCountExceededException.class);
    }
}
