package com.backtoback.reseat.domain.admin.seatinventory.service;

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
 * AdminGameSeatStatusService 단위 테스트.
 * <p>서비스가 좌석을 조회해 올바른 엔티티 메서드에 위임하는지를 검증한다.
 * 상태 전이 규칙(AVAILABLE만 block 가능, BLOCKED만 unblock 가능 등) 자체는 GameSeatTest에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AdminGameSeatStatusServiceTest {

    @Mock
    private GameSeatRepository gameSeatRepository;
    @Mock
    private GameSeat gameSeat;

    private AdminGameSeatStatusService adminGameSeatStatusService;

    @Test
    @DisplayName("blockSeat 호출 시 조회한 좌석의 block()을 호출한다")
    void blockSeat_delegatesToEntity_whenSeatExists() {
        adminGameSeatStatusService = new AdminGameSeatStatusService(gameSeatRepository);
        when(gameSeatRepository.findById(1L)).thenReturn(Optional.of(gameSeat));

        adminGameSeatStatusService.blockSeat(1L, "매크로 의심 좌석 임시 차단");

        verify(gameSeat).block();
    }

    @Test
    @DisplayName("존재하지 않는 좌석 ID로 blockSeat를 호출하면 GameSeatNotFoundException이 발생한다")
    void blockSeat_throws_whenSeatNotFound() {
        adminGameSeatStatusService = new AdminGameSeatStatusService(gameSeatRepository);
        when(gameSeatRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminGameSeatStatusService.blockSeat(999L, "사유"))
            .isInstanceOf(GameSeatNotFoundException.class);
    }

    @Test
    @DisplayName("unblockSeat 호출 시 조회한 좌석의 unblock()을 호출한다")
    void unblockSeat_delegatesToEntity_whenSeatExists() {
        adminGameSeatStatusService = new AdminGameSeatStatusService(gameSeatRepository);
        when(gameSeatRepository.findById(1L)).thenReturn(Optional.of(gameSeat));

        adminGameSeatStatusService.unblockSeat(1L, "차단 사유 해소");

        verify(gameSeat).unblock();
    }

    @Test
    @DisplayName("존재하지 않는 좌석 ID로 unblockSeat를 호출하면 GameSeatNotFoundException이 발생한다")
    void unblockSeat_throws_whenSeatNotFound() {
        adminGameSeatStatusService = new AdminGameSeatStatusService(gameSeatRepository);
        when(gameSeatRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminGameSeatStatusService.unblockSeat(999L, "사유"))
            .isInstanceOf(GameSeatNotFoundException.class);
    }
}
