package com.backtoback.reseat.domain.reservation.service.lock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.reservation.exception.LockFailedException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redisson 기반 좌석 단위 분산 락 구현체.
 * <p>락 키: {@code lock:game-seat:{gameSeatId}}</p>
 * <p>데드락 방지: {@code gameSeatId} 오름차순 획득, 역순 해제</p>
 * <p>락 해제 시점: action(트랜잭션) 완료 후 finally — 커밋 전 해제 시 over-booking 재발</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonSeatLockStrategy implements SeatLockStrategy {

	// 락 획득 대기 시간: 빠른 실패로 경합 요청을 즉시 거부한다
	private static final long WAIT_SECONDS = 3L;

	private static final String LOCK_KEY_PREFIX = "lock:game-seat:";

	private final RedissonClient redissonClient;

	/**
	 * <p>획득 순서: gameSeatIds 오름차순 정렬 후 순차 획득</p>
	 * <p>해제 순서: 역순 — finally 블록에서 isHeldByCurrentThread() 확인 후 해제</p>
	 *
	 * @throws LockFailedException 락 획득 실패 또는 스레드 인터럽트 시
	 */
	@Override
	public <T> T executeWithLocks(List<Long> gameSeatIds, Supplier<T> action) {
		// 방어적 복사 후 오름차순 정렬 — 원본 리스트 불변성 보호 + 데드락 조건 차단
		List<Long> sortedIds = new ArrayList<>(gameSeatIds);
		sortedIds.sort(null);

		List<RLock> acquiredLocks = new ArrayList<>();

		try {
			for (Long gameSeatId : sortedIds) {
				RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + gameSeatId);

				// leaseTime을 명시하지 않아 Redisson watchdog가 락을 자동 갱신한다.
				boolean acquired = lock.tryLock(WAIT_SECONDS, TimeUnit.SECONDS);

				if (!acquired) {
					log.warn("좌석 락 획득 실패 - gameSeatId: {}", gameSeatId);
					throw new LockFailedException();
				}

				acquiredLocks.add(lock);
			}

			return action.get();

		} catch (InterruptedException e) {
			// interrupt 상태 복원 후 락 실패로 변환
			Thread.currentThread().interrupt();
			throw new LockFailedException("좌석 락 획득 중 스레드가 중단되었습니다.");

		} finally {
			// 획득 역순 해제 — isHeldByCurrentThread()로 소유권 확인 후 안전하게 해제
			for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
				RLock lock = acquiredLocks.get(i);
				if (lock.isHeldByCurrentThread()) {
					lock.unlock();
				}
			}
		}
	}
}
