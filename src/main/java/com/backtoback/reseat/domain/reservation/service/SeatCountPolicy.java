package com.backtoback.reseat.domain.reservation.service;

import com.backtoback.reseat.domain.reservation.exception.MaxSeatCountExceededException;

/**
 * 좌석 선점 수량 정책.
 * <p>
 * 1인당 한 경기 최대 2좌석.
 * SeatHoldRequest.gameSeatIds는 DTO 검증(@Size max=2)으로 요청 단위 초과를 이미 막고 있으므로,
 * 이 클래스는 여러 번 나눠 요청하는 "누적" 경로만 책임진다.
 */
public final class SeatCountPolicy {

    /**
     * 1인당 한 경기 최대 보유 좌석 수
     */
    public static final int MAX_SEAT_COUNT_PER_GAME = 2;

    private SeatCountPolicy() {}

    /**
     * 누적 보유 좌석 수와 요청 좌석 수의 합이 상한을 넘는지 검증한다.
     *
     * @param heldSeatCount 현재 보유 좌석 수 (유효 HOLDING 예약 + 유효 티켓)
     * @param requestedCount 이번 요청의 좌석 수
     * @throws MaxSeatCountExceededException 합이 상한을 초과한 경우
     */
    public static void validateSeatCount(int heldSeatCount, int requestedCount) {
        if (heldSeatCount + requestedCount > MAX_SEAT_COUNT_PER_GAME) {
            throw new MaxSeatCountExceededException();
        }
    }
}
