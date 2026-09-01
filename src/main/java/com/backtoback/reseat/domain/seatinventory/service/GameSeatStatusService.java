package com.backtoback.reseat.domain.seatinventory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.exception.GameSeatNotFoundException;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;

import lombok.RequiredArgsConstructor;

/**
 * 경기 좌석 상태 변경 서비스
 */
@Service
@RequiredArgsConstructor
public class GameSeatStatusService {

    private final GameSeatRepository gameSeatRepository;

    /**
     * 경기 좌석을 예매 가능 상태로 되돌린다.
     *
     * @param gameSeatId 예매 가능 상태로 변경할 경기 좌석 ID
     */
    @Transactional
    public void releaseSeat(Long gameSeatId) {

        GameSeat gameSeat = gameSeatRepository.findById(gameSeatId).orElseThrow(GameSeatNotFoundException::new);
        gameSeat.available();
    }
}
