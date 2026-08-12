package com.backtoback.reseat.domain.queue.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backtoback.reseat.domain.queue.entity.QueueEntryHistory;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;

import jakarta.persistence.LockModeType;

/**
 * 경기별 사용자 대기열 진입 이력의 저장과 조회를 담당하는 Repository
 */
public interface QueueEntryHistoryRepository extends JpaRepository<QueueEntryHistory, Long> {

    // 경기와 사용자를 조합한 고유 대기열 식별값으로 진입 이력을 조회한다.
    Optional<QueueEntryHistory> findByQueueKey(String queueKey);

    // 동일 사용자가 다른 경기에서 대기 중인지 확인한다.
    boolean existsByUser_IdAndGame_IdNotAndStatus(Long userId, Long gameId, QueueEntryHistoryStatus status);

    /**
     * 대기열 취소와 입장 허용의 동시 상태 변경을 막기 위해 대기 이력을 비관적 락으로 조회한다.
     *
     * @param queueKey 경기와 사용자를 조합한 고유 대기열 식별값
     * @return 비관적 락으로 조회한 대기 이력
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT qeh
        FROM QueueEntryHistory qeh
        WHERE qeh.queueKey = :queueKey
        """)
    Optional<QueueEntryHistory> findByQueueKeyWithPessimisticWriteLock(@Param("queueKey") String queueKey);
}
