package com.backtoback.reseat.domain.reservation.service;

import com.backtoback.reseat.domain.queue.service.AdmissionTokenService;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationResponse;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.domain.reservation.service.lock.SeatLockStrategy;
import com.backtoback.reseat.domain.reservation.service.port.TicketCountPort;
import com.backtoback.reseat.domain.reservation.service.port.UserVerificationPort;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Disabled("테스트 제외")
@ExtendWith(MockitoExtension.class)
class SeatHoldFacadeBrowsingTest {

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

    private SeatHoldFacade seatHoldFacade;
    private SeatHoldRequest request;

    @BeforeEach
    void setUp() {
        seatHoldFacade
            = new SeatHoldFacade(
                reservationService,
                seatLockStrategy,
                admissionTokenService,
                reservationSeatRepository,
                ticketCountPort,
                userVerificationPort
            );

        request = new SeatHoldRequest(GAME_ID, List.of(101L));

        when(userVerificationPort.isVerified(USER_ID)).thenReturn(true);

        when(reservationSeatRepository.countActiveHoldingSeats(eq(USER_ID), eq(GAME_ID), any(LocalDateTime.class)))
            .thenReturn(0);
        when(ticketCountPort.countActiveTickets(USER_ID, GAME_ID)).thenReturn(0);

        ReservationResponse holdingResponse
            = new ReservationResponse(
                1001L,
                "RSV-TEST",
                ReservationStatus.HOLDING,
                List.of(),
                LocalDateTime.now(),
                LocalDateTime.now()
            );
        when(reservationService.holdSeats(USER_ID, request)).thenReturn(holdingResponse);
    }

    @SuppressWarnings("unchecked")
    private void stubLockPassthrough() {
        when(seatLockStrategy.executeWithLocks(anyList(), any(Supplier.class)))
            .thenAnswer(invocation -> ((Supplier<ReservationResponse>)invocation.getArgument(1)).get());
    }

    @Test
    @DisplayName("좌석 선점에 성공하면 좌석 탐색 완료 기록이 한 번 호출된다")
    void should_callCompleteSeatBrowsingOnce_when_holdSucceeds() {
        stubLockPassthrough();

        seatHoldFacade.holdSeats(USER_ID, TOKEN, request);

        verify(admissionTokenService, times(1)).completeSeatBrowsing(USER_ID, GAME_ID, TOKEN);
    }

    @Test
    @DisplayName("좌석 탐색 완료 기록이 실패해도 좌석 선점은 성공으로 유지된다")
    void should_returnResponse_when_completeSeatBrowsingThrows() {
        stubLockPassthrough();
        doThrow(new RuntimeException("기록 실패"))
            .when(admissionTokenService)
            .completeSeatBrowsing(anyLong(), anyLong(), anyString());

        ReservationResponse response = seatHoldFacade.holdSeats(USER_ID, TOKEN, request);

        assertThat(response.status()).isEqualTo(ReservationStatus.HOLDING);
    }

    @Test
    @DisplayName("동일 토큰으로 2회 선점해도 탐색 완료 기록 호출 자체는 매번 발생한다 (멱등성은 AdmissionToken 엔티티 책임)")
    void should_callCompleteSeatBrowsingEachTime_when_holdCalledTwice() {
        stubLockPassthrough();

        seatHoldFacade.holdSeats(USER_ID, TOKEN, request);
        seatHoldFacade.holdSeats(USER_ID, TOKEN, request);

        verify(admissionTokenService, times(2)).completeSeatBrowsing(USER_ID, GAME_ID, TOKEN);
    }
}
