package com.backtoback.reseat.domain.seatinventory.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.queue.exception.QueueTokenRevokedException;
import com.backtoback.reseat.domain.queue.service.AdmissionTokenService;
import com.backtoback.reseat.domain.seatinventory.dto.SeatStatusResponse;
import com.backtoback.reseat.domain.seatinventory.service.SeatQueryService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;

/**
 * GameSeatController의 Queue-Token 검증 위임 여부만 검증하는 단위 테스트.
 * <p>
 * REVOKED 전파만 좁게 검증하는 순수 Mockito 테스트로 분리한다.
 * DB·Redis 의존이 없어 Testcontainers 없이 바로 실행된다.
 */
@ExtendWith(MockitoExtension.class)
class GameSeatControllerQueueTokenTest {

    private static final Long USER_ID = 1L;
    private static final Long GAME_ID = 10L;
    private static final String TOKEN = "qt_test-token";

    @Mock
    private SeatQueryService seatQueryService;
    @Mock
    private AdmissionTokenService admissionTokenService;

    private GameSeatController gameSeatController;

    private final CustomUserDetails userDetails = CustomUserDetails.of(USER_ID, "user@test.com", "USER");

    @Test
    @DisplayName("유효한 토큰으로 조회하면 좌석 현황을 정상 반환한다")
    void should_return200_when_tokenValid() {
        // given
        gameSeatController = new GameSeatController(seatQueryService, admissionTokenService);
        List<SeatStatusResponse> seats = List.of(mock(SeatStatusResponse.class));
        when(seatQueryService.getSeats(GAME_ID, null, null, null)).thenReturn(seats);

        // when
        ResponseEntity<ApiResponse<List<SeatStatusResponse>>> response
            = gameSeatController.getSeats(GAME_ID, null, null, null, TOKEN, userDetails);

        // then
        assertThat(response.getBody().getData()).isEqualTo(seats);
        verify(admissionTokenService).validateToken(USER_ID, GAME_ID, TOKEN);
    }

    @Test
    @DisplayName("폐기된(REVOKED) 토큰으로 조회하면 QueueTokenRevokedException이 전파되고 좌석 조회를 시도하지 않는다")
    void should_propagateRevoked_when_tokenRevoked() {
        // given
        gameSeatController = new GameSeatController(seatQueryService, admissionTokenService);
        doThrow(new QueueTokenRevokedException()).when(admissionTokenService).validateToken(USER_ID, GAME_ID, TOKEN);

        // when & then
        assertThatThrownBy(() -> gameSeatController.getSeats(GAME_ID, null, null, null, TOKEN, userDetails))
            .isInstanceOf(QueueTokenRevokedException.class);

        // 토큰 검증에서 이미 차단됐으므로 좌석 조회 자체를 시도하면 안 된다
        verifyNoInteractions(seatQueryService);
    }
}
