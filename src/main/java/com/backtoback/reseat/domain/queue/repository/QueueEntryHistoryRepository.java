package com.backtoback.reseat.domain.queue.repository;

import com.backtoback.reseat.domain.queue.entity.QueueEntryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QueueEntryHistoryRepository extends JpaRepository<QueueEntryHistory, Long> {

    // queue_key로 경기-사용자 진입 이력을 단건 조회한다.
    Optional<QueueEntryHistory> findByQueueKey(String queueKey);
}
