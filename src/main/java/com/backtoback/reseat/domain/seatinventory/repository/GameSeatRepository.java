package com.backtoback.reseat.domain.seatinventory.repository;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameSeatRepository extends JpaRepository<GameSeat, Long> {

    /**
     * 해당 경기의 좌석 재고가 이미 생성되었는지 확인한다.
     *
     * @param gameId 경기 ID
     * @return       재고가 1건 이상 존재하면 true
     */
    boolean existsByGameId(Long gameId);

    /**
     * 경기의 좌석 현황을 seat·zone과 함께 조회한다.
     *
     * <p>GameSeat → Seat → SeatZone 3단계 fetch join으로 N+1을 방지한다.
     *
     * @param gameId 경기 ID
     * @return 좌석 현황 목록 (seat·zone 로딩 완료)
     */
    @Query("""
            select gs
            from GameSeat gs
            join fetch gs.seat s
            join fetch s.zone z
            where gs.game.id = :gameId
            order by z.id asc, s.seatRow asc, s.seatNumber asc
            """)
    List<GameSeat> findAllByGameIdWithSeatAndZone(@Param("gameId") Long gameId);

    /**
     * 경기의 좌석 현황을 구역·등급·상태 필터로 조회한다.
     *
     * <p>필터가 null이면 해당 조건을 무시한다.
     * <p>QueryDSL 미사용 환경에서 JPQL 동적 조건으로 처리한다.
     *
     * @param gameId   경기 ID (필수)
     * @param zoneId   구역 ID (선택)
     * @param grade    좌석 등급 (선택)
     * @param status   좌석 상태 (선택)
     * @return 필터 적용된 좌석 현황 목록
     */
    @Query("""
            select gs
            from GameSeat gs
            join fetch gs.seat s
            join fetch s.zone z
            where gs.game.id = :gameId
              and (:zoneId is null or z.id = :zoneId)
              and (:grade is null or z.grade = :grade)
              and (:status is null or gs.status = :status)
            order by z.id asc, s.seatRow asc, s.seatNumber asc
            """)
    List<GameSeat> findAllByGameIdWithFilters(
        @Param("gameId") Long gameId,
        @Param("zoneId") Long zoneId,
        @Param("grade") SeatGrade grade,
        @Param("status") GameSeatStatus status
    );


    // 이후에 추가 예정:
    //   findByIdWithPessimisticLock(Long id)  — @Lock(PESSIMISTIC_WRITE)
    //   findExpiredHeld(LocalDateTime now)
    //   countByGameIdAndStatus(Long gameId, GameSeatStatus status)
}
