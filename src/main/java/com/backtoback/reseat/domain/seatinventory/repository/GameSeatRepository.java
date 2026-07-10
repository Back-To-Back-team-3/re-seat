package com.backtoback.reseat.domain.seatinventory.repository;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSeatRepository extends JpaRepository<GameSeat, Long> {

    /**
     * 해당 경기의 좌석 재고가 이미 생성되었는지 확인한다.
     *
     * @param gameId 경기 ID
     * @return       재고가 1건 이상 존재하면 true
     */
    boolean existsBySeatId(Long gameId);

    // 이후에 추가 예정:
    //   findByIdWithPessimisticLock(Long id)  — @Lock(PESSIMISTIC_WRITE)
    //   findExpiredHeld(LocalDateTime now)
    //   countByGameIdAndStatus(Long gameId, GameSeatStatus status)
}
