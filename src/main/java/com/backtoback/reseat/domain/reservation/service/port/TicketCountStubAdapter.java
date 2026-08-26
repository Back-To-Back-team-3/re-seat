package com.backtoback.reseat.domain.reservation.service.port;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * TicketCountPort의 임시 구현체.
 * <p>
 * 티켓 도메인의 티켓 수 조회 계약이 확정되기 전까지 0을 반환한다.
 * 이 상태에서는 결제 완료(CONFIRMED) 이후 경로의 누적 수량 검증이 동작하지 않는다.
 * → T3-02 완료 기준 감산 사유. 티켓 계약 확정 후 실제 구현으로 교체한다.
 */
@Slf4j
@Component
public class TicketCountStubAdapter implements TicketCountPort {

    @Override
    public int countActiveTickets(Long userId, Long gameId) {
        log.warn("티켓 수 집계 미구현 - 결제 완료 이후 수량 검증이 동작하지 않습니다. userId={}, gameId={}", userId, gameId);
        return 0;
    }
}
