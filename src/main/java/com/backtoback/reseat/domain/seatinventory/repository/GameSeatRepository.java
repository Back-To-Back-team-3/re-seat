package com.backtoback.reseat.domain.seatinventory.repository;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSeatRepository extends JpaRepository<GameSeat, Long> {
    // 이후에 추가 예정:
    //   findByIdWithPessimisticLock(Long id)  — @Lock(PESSIMISTIC_WRITE)
    //   findExpiredHeld(LocalDateTime now)
    //   countByGameIdAndStatus(Long gameId, GameSeatStatus status)
}
