package com.backtoback.reseat.domain.reservation.service.lock;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.reservation.exception.LockFailedException;
import com.backtoback.reseat.domain.seatinventory.exception.GameSeatNotFoundException;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SELECT FOR UPDATE 기반 비관적 락 전략.
 * <p>
 * 락 해제 시점: 이 메서드의 트랜잭션이 커밋되는 순간.
 * DB 행 락은 커밋과 동시에 자동 해제되므로 Redisson 전략과 달리 별도의 unlock 호출이 필요 없다.
 * 이때 {@code action}(실제 좌석 선점 로직)은 이 메서드가 연 트랜잭션에 REQUIRED 전파로 참여해야 한다.
 * {@code action}이 REQUIRES_NEW로 새 트랜잭션을 열면 같은 스레드 안에서 자신이 건 행 락을 기다리다 교착 상태에 빠질 수 있다.
 * <p>
 * 다좌석 요청 시 좌석 ID 오름차순으로 락을 획득해 데드락을 방지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PessimisticLockStrategy implements SeatLockStrategy {

    private final GameSeatRepository gameSeatRepository;

    @Override
    @Transactional
    public <T> T executeWithLocks(List<Long> gameSeatIds, Supplier<T> action) {
        List<Long> sortedIds = gameSeatIds.stream().sorted().toList();
        try {
            for (Long id : sortedIds) {
                gameSeatRepository.findByIdWithPessimisticLock(id).orElseThrow(GameSeatNotFoundException::new);
            }
            return action.get();
        } catch (PessimisticLockingFailureException e) {
            // Spring Data JPA 리포지토리 프록시가 jakarta.persistence.PessimisticLockException·LockTimeoutException을
            // 이 타입(및 하위 타입 CannotAcquireLockException)으로 이미 번역한 뒤 여기로 전달한다.
            log.warn("좌석 비관적 락 획득 실패 - gameSeatIds: {}", sortedIds);
            throw new LockFailedException();
        }
    }
}
