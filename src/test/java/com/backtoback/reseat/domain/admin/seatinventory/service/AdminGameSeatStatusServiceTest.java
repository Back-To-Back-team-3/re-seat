package com.backtoback.reseat.domain.admin.seatinventory.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.exception.GameSeatNotFoundException;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;

/**
 * AdminGameSeatStatusService(좌석 판매 차단·해제) 단위 테스트.
 * <p>GameSeat 자체의 상태 전이 검증은 GameSeatTest가 담당하므로,
 * 이 테스트에서는 서비스가 조회·위임을 올바르게 수행하는지만 mock으로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminGameSeatStatusServiceTest {

    @Mock
    private GameSeatRepository gameSeatRepository;

    @InjectMocks
    private AdminGameSeatStatusService adminGameSeatStatusService;

    @DisplayName("존재하는 좌석을 차단하면 GameSeat.block()이 호출된다")
    @Test
    void should_callBlock_when_gameSeatExists() {
        GameSeat gameSeat = mock(GameSeat.class);
        when(gameSeatRepository.findById(5001L)).thenReturn(Optional.of(gameSeat));

        adminGameSeatStatusService.blockSeat(5001L, "매크로 의심 좌석 임시 차단");

        verify(gameSeat).block();
    }

    @DisplayName("존재하지 않는 좌석을 차단하면 GameSeatNotFoundException이 발생한다")
    @Test
    void should_throwException_when_gameSeatNotFoundOnBlock() {
        when(gameSeatRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminGameSeatStatusService.blockSeat(999L, "사유"))
            .isInstanceOf(GameSeatNotFoundException.class);
    }

    @DisplayName("존재하는 좌석을 해제하면 GameSeat.unblock()이 호출된다")
    @Test
    void should_callUnblock_when_gameSeatExists() {
        GameSeat gameSeat = mock(GameSeat.class);
        when(gameSeatRepository.findById(5001L)).thenReturn(Optional.of(gameSeat));

        adminGameSeatStatusService.unblockSeat(5001L, "차단 사유 해소");

        verify(gameSeat).unblock();
    }

    @DisplayName("존재하지 않는 좌석을 해제하면 GameSeatNotFoundException이 발생한다")
    @Test
    void should_throwException_when_gameSeatNotFoundOnUnblock() {
        when(gameSeatRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminGameSeatStatusService.unblockSeat(999L, "사유"))
            .isInstanceOf(GameSeatNotFoundException.class);
    }
}
