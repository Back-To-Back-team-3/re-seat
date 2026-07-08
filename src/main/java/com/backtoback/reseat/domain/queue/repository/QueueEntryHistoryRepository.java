package com.backtoback.reseat.domain.queue.repository;

import com.backtoback.reseat.domain.queue.entity.QueueEntryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QueueEntryHistoryRepository extends JpaRepository<QueueEntryHistory, Long> {

    Optional<QueueEntryHistory> findByQueueKey(String queueKey);
}
