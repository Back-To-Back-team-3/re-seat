package com.backtoback.reseat.domain.seatinventory.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backtoback.reseat.domain.seatinventory.dto.ZoneSummaryResponse;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;

public interface GameSeatRepository extends JpaRepository<GameSeat, Long> {

	/**
	 * 해당 경기의 좌석 재고가 이미 생성되었는지 확인한다.
	 *
	 * @param gameId 경기 ID
	 * @return 재고가 1건 이상 존재하면 true
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
	List<GameSeat> findAllByGameIdWithSeatAndZone(@Param("gameId")
	Long gameId);

	/**
	 * 경기의 좌석 현황을 구역·등급·상태 필터로 조회한다.
	 *
	 * <p>필터가 null이면 해당 조건을 무시한다.
	 * <p>QueryDSL 미사용 환경에서 JPQL 동적 조건으로 처리한다.
	 *
	 * @param gameId 경기 ID (필수)
	 * @param zoneId 구역 ID (선택)
	 * @param grade  좌석 등급 (선택)
	 * @param status 좌석 상태 (선택)
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
		@Param("gameId")
		Long gameId,
		@Param("zoneId")
		Long zoneId,
		@Param("grade")
		SeatGrade grade,
		@Param("status")
		GameSeatStatus status);

	/**
	 * 경기의 구역별 잔여 좌석 수를 집계한다.
	 *
	 * <p>SeatZone을 기준으로 LEFT JOIN해 잔여수가 0인 구역도 결과에 포함한다.
	 * AVAILABLE 상태인 game_seats만 카운트한다.
	 *
	 * <p>현재 totalCount는 50으로 고정 (V4 시드 기준 구역당 50석).
	 * 구장·구역 구성이 바뀌면 COUNT(s)로 교체한다.
	 *
	 * @param gameId    경기 ID
	 * @param stadiumId 구장 ID (해당 구장의 구역만 조회)
	 * @return 구역별 요약 목록
	 */
	@Query("""
		select new com.backtoback.reseat.domain.seatinventory.dto.ZoneSummaryResponse(
		    z.id,
		    z.name,
		    z.grade,
		    z.basePrice,
		    50,
		    cast(count(case when gs.status = 'AVAILABLE' then 1 end) as int)
		)
		from SeatZone z
		left join GameSeat gs on gs.seat.zone.id = z.id
		                     and gs.game.id = :gameId
		where z.stadium.id = :stadiumId
		group by z.id, z.name, z.grade, z.basePrice
		order by z.id asc
		""")
	List<ZoneSummaryResponse> findZoneSummariesByGameId(
		@Param("gameId")
		Long gameId,
		@Param("stadiumId")
		Long stadiumId);

	// 이후에 추가 예정:
	//   findByIdWithPessimisticLock(Long id)  — @Lock(PESSIMISTIC_WRITE)
	//   countByGameIdAndStatus(Long gameId, GameSeatStatus status)

	/**
	 * HELD 상태이면서 선점 만료 시각이 지난 좌석을 AVAILABLE로 벌크 회수한다.
	 * <p>
	 * SELECT → 판정 → UPDATE 방식은 주문 생성의 선점 연장 트랜잭션과 lost update 경합이 발생한다.
	 * WHERE 절에 만료 조건을 직접 실어 DB 원자성으로 경합을 흡수한다. (API 명세서 5.1 선점 만료 경합 주의, 관련 버그 B5)
	 * <p>
	 * 벌크 UPDATE는 JPA 1차 캐시를 우회하므로 clearAutomatically = true로
	 * 같은 트랜잭션 내 후속 조회의 stale 상태를 방지한다.
	 * <p>
	 * 인덱스: idx_game_seats_hold_expires(status, hold_expires_at)
	 *
	 * @param now 만료 판정 기준 시각 (HoldExpiryService가 주입)
	 * @return 회수된 좌석 행 수
	 */
	@Modifying(clearAutomatically = true)
	@Query("""
		update GameSeat gs
		   set gs.status = :available,
		       gs.holdExpiresAt = null
		 where gs.status = :held
		   and gs.holdExpiresAt < :now
		""")
	int releaseExpiredSeats(
		@Param("now")
		LocalDateTime now,
		@Param("held")
		GameSeatStatus held,
		@Param("available")
		GameSeatStatus available);
}
