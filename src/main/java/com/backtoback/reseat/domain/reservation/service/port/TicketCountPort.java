package com.backtoback.reseat.domain.reservation.service.port;

/**
 * 티켓 도메인의 유효 티켓 수 조회 계약.
 * <p>
 * 결제 완료(CONFIRMED) 이후 경로의 누적 보유 좌석 수 집계에 사용한다.
 * 집계 대상: tickets.status가 ISSUED·REFUND_PENDING·REFUND_FAILED인 티켓 수.
 */
public interface TicketCountPort {

    int countActiveTickets(Long userId, Long gameId);
}
