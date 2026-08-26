package com.backtoback.reseat.domain.reservation.service.lock;

import java.util.function.Supplier;

/**
 * 사용자·경기 단위 락 전략 인터페이스.
 * <p>좌석 단위 락(SeatLockStrategy)과 별개로, 동일 사용자가 동일 경기에 대해
 * 동시에 여러 요청(서로 다른 좌석)을 보내는 경우의 수량 검증 원자성을 보장한다.</p>
 * <p>락 키 단위: {@code userId + gameId} 조합 — 좌석 ID와 무관하게 사용자·경기 조합마다 하나.</p>
 * <p>락 해제 시점: {@code action}(수량 검증 + 좌석 락 + 선점 트랜잭션) 완료 후 — 커밋 전 해제 시
 * 이 락을 도입한 목적(누적 수량 원자성)이 무효화된다.</p>
 * <p>서로 다른 사용자의 요청은 병렬 처리 — 전역 락으로 직렬화되면 설계 실패.</p>
 * <p>락 획득 실패 시 구현체는 LockFailedException을 발생시켜 LOCK_FAILED(409)로 응답한다.</p>
 */
public interface UserGameLockStrategy {

    /**
     * 지정한 사용자·경기 조합에 락을 걸고 {@code action}을 실행한 뒤 락을 해제한다.
     *
     * @param <T> action의 반환 타입
     * @param userId 락 대상 사용자 ID
     * @param gameId 락 대상 경기 ID
     * @param action 락 보호 구간에서 실행할 로직 — 수량 검증 + 좌석 락 + 선점 트랜잭션 포함
     * @return action의 반환값
     * @throws com.backtoback.reseat.domain.reservation.exception.LockFailedException 락 획득 실패 시
     */
    <T> T executeWithLock(Long userId, Long gameId, Supplier<T> action);
}
