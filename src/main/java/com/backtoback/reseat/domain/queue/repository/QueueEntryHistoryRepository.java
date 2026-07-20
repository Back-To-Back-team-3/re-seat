package com.backtoback.reseat.domain.queue.repository;

import com.backtoback.reseat.domain.queue.entity.QueueEntryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 경기별 사용자 대기열 진입 이력의 저장과 조회를 담당하는 Repository
 */
public interface QueueEntryHistoryRepository extends JpaRepository<QueueEntryHistory, Long> {

    // 경기와 사용자를 조합한 고유 대기열 식별값으로 진입 이력을 조회한다.
    Optional<QueueEntryHistory> findByQueueKey(String queueKey);
}
