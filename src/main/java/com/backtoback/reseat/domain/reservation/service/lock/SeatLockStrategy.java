package com.backtoback.reseat.domain.reservation.service.lock;

import java.util.List;
import java.util.function.Supplier;

/**
 * 좌석 분산 락 전략 인터페이스.
 *
 * <p>Facade가 락 기술(Redisson·비관적·낙관적)을 직접 알지 않아도 되도록 락 획득·실행·해제 흐름을 캡슐화한다.</p>
 * <p>락 획득 순서: {@code gameSeatIds} 오름차순 — 데드락 조건(순환 대기) 원천 차단</p>
 * <p>락 해제 순서: 역순(획득 역순) — 잠금 계층 일관성 유지</p>
 * <p>락 해제 시점: {@code action} 내부 트랜잭션 커밋 완료 이후 — 커밋 전 해제 시 over-booking 재발</p>
 * <p>서로 다른 {@code gameSeatId} 요청은 병렬 처리 — 글로벌 락 직렬화 금지</p>
 * <p>락 획득 실패 시 구현체는 LockFailedException을 발생시켜 LOCK_FAILED(409)로 응답한다.</p>
 */
public interface SeatLockStrategy {

	/**
	 * 지정한 좌석 목록에 락을 걸고 {@code action}을 실행한 뒤 락을 해제한다.
	 *
	 * <p>구현체는 gameSeatIds를 오름차순 정렬 후 락을 순서대로 획득하고, action 완료(커밋) 후 역순으로 해제한다.</p>
	 *
	 * @param <T>         action의 반환 타입
	 * @param gameSeatIds 락 대상 경기 좌석 ID 목록 (비어 있으면 락 없이 action 실행)
	 * @param action      락 보호 구간에서 실행할 로직 — 내부에 @Transactional 메서드 호출 포함
	 * @return action의 반환값
	 * @throws com.backtoback.reseat.domain.reservation.exception.LockFailedException 락 획득 실패 시
	 */
	<T> T executeWithLocks(List<Long> gameSeatIds, Supplier<T> action);
}
