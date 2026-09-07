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

    /**
     * 판매 완료(SOLD) 좌석을 환불 확정 시점에 예매 가능(AVAILABLE) 상태로 되돌린다.
     * <p>호출 시점은 반드시 티켓 환불이 REFUNDED로 확정된 이후여야 한다.
     * REFUND_PENDING·REFUND_FAILED 구간에서 호출하면 환불 실패 시 복구가 불가능해진다.</p>
     *
     * @param gameSeatId 환불 확정된 좌석의 경기 좌석 ID
     */
    @Transactional
    public void refundSeat(Long gameSeatId) {

        GameSeat gameSeat = gameSeatRepository.findById(gameSeatId).orElseThrow(GameSeatNotFoundException::new);
        gameSeat.refund();
    }
}
