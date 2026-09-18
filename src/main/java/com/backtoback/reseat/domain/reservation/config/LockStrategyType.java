package com.backtoback.reseat.domain.reservation.config;

/**
 * 락 전략 3종 비교(T3-11) 대상 전략 목록.
 * application.yaml의 reservation.lock-strategy 값과 1:1 매핑된다.
 */
public enum LockStrategyType {
    DISTRIBUTED,   // RedissonSeatLockStrategy (기본값)
    PESSIMISTIC,   // PessimisticLockStrategy
    OPTIMISTIC     // OptimisticLockStrategy
}
