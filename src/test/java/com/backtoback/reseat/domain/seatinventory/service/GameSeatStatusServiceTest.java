package com.backtoback.reseat.domain.seatinventory.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.exception.GameSeatNotFoundException;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;

/**
 * GameSeatStatusService 단위 테스트.
 * <p> 서비스가 좌석을 조회해 올바른 엔티티 메서드에 위임하는지를 검증한다.
 * 상태 전이 규칙(SOLD만 refund 가능 등) 자체는 GameSeatTest에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class GameSeatStatusServiceTest {

    @Mock
    private GameSeatRepository gameSeatRepository;
    @Mock
    private GameSeat gameSeat;

    private GameSeatStatusService gameSeatStatusService;

    @Test
    @DisplayName("releaseSeat 호출 시 조회한 좌석의 available()을 호출한다 (기존 동작 회귀 확인)")
    void releaseSeat_delegatesToEntity_whenSeatExists() {
        gameSeatStatusService = new GameSeatStatusService(gameSeatRepository);
        when(gameSeatRepository.findById(1L)).thenReturn(Optional.of(gameSeat));

        gameSeatStatusService.releaseSeat(1L);

        verify(gameSeat).available();
    }

    @Test
    @DisplayName("refundSeat 호출 시 조회한 좌석의 refund()를 호출한다")
    void refundSeat_delegatesToEntity_whenSeatExists() {
        gameSeatStatusService = new GameSeatStatusService(gameSeatRepository);
        when(gameSeatRepository.findById(1L)).thenReturn(Optional.of(gameSeat));

        gameSeatStatusService.refundSeat(1L);

        verify(gameSeat).refund();
    }

    @Test
    @DisplayName("존재하지 않는 좌석 ID로 refundSeat를 호출하면 GameSeatNotFoundException이 발생한다")
    void refundSeat_throws_whenSeatNotFound() {
        gameSeatStatusService = new GameSeatStatusService(gameSeatRepository);
        when(gameSeatRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameSeatStatusService.refundSeat(999L)).isInstanceOf(GameSeatNotFoundException.class);
    }
}
