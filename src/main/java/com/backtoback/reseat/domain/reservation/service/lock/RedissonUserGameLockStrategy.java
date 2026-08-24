package com.backtoback.reseat.domain.reservation.service.lock;

import com.backtoback.reseat.domain.reservation.exception.LockFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 기반 사용자·경기 단위 분산 락 구현체.
 * <p>락 키: {@code lock:seat-hold:user:{userId}:game:{gameId}}</p>
 * <p>좌석 단위 락(RedissonSeatLockStrategy, {@code lock:game-seat:*})과 키 네임스페이스를 분리해
 * 서로 다른 목적의 락이 절대 충돌하지 않도록 한다.</p>
 * <p>대기 시간을 좌석 락(3초)보다 길게(5초) 두었다 — 이 락 내부에서 좌석 락 전체를 감싸기 때문이다.</p>
 * <p>호출 순서: 반드시 사용자 락을 바깥에서 획득한 뒤 좌석 락을 안에서 획득한다(데드락 방지).
 * 이 순서를 뒤집는 코드가 추가되면 순환 대기가 발생할 수 있다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonUserGameLockStrategy implements UserGameLockStrategy {

    // 락 획득 대기 시간: 내부에서 좌석 락 전체를 감싸므로 좌석 락(3초)보다 길게 설정
    private static final long WAIT_SECONDS = 5L;

    private static final String LOCK_KEY_PREFIX = "lock:seat-hold:user:";

    private final RedissonClient redissonClient;

    /**
     * @throws LockFailedException 락 획득 실패 또는 스레드 인터럽트 시
     */
    @Override
    public <T> T executeWithLock(Long userId, Long gameId, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey(userId, gameId));
        boolean acquired = false;

        try {
            // leaseTime을 명시하지 않아 Redisson watchdog가 락을 자동 갱신한다.
            acquired = lock.tryLock(WAIT_SECONDS, TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("사용자·경기 락 획득 실패 - userId: {}, gameId: {}", userId, gameId);
                throw new LockFailedException();
            }

            return action.get();

        } catch (InterruptedException e) {
            // interrupt 상태 복원 후 락 실패로 변환
            Thread.currentThread().interrupt();
            throw new LockFailedException("사용자·경기 락 획득 중 스레드가 중단되었습니다.");

        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String lockKey(Long userId, Long gameId) {
        return LOCK_KEY_PREFIX + userId + ":game:" + gameId;
    }
}
