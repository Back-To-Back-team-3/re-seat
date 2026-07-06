package com.backtoback.reseat.domain.queue.repository;

import com.backtoback.reseat.domain.queue.entity.QueueEntryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueEntryHistoryRepository extends JpaRepository<QueueEntryHistory, Long> {
}
