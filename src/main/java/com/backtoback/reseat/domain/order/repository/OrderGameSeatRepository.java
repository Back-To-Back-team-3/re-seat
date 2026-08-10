package com.backtoback.reseat.domain.order.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;

/**
 * 주문 만료 처리 과정에서 GameSeat 선점 해제를 담당하는 Repository
 */
public interface OrderGameSeatRepository extends JpaRepository<GameSeat, Long> {

	/**
	 * 결제 기한 만료 주문과 연결된 HELD 경기 좌석을 AVAILABLE로 벌크 전이한다.
	 *
	 * <p>좌석 상태를 AVAILABLE로 변경하면서 선점 만료 시간을 함께 초기화 한다.</p>
	 *
	 * @param now          만료 판정 기준 시간
	 * @param held         선점 해제 대상 좌석 상태
	 * @param orderExpired 연결된 주문의 만료 상태
	 * @param available    변경할 좌석 상태
	 * @return AVAILABLE로 전이된 경기 좌석 수
	 */
	@Modifying(clearAutomatically = true)
	@Query("""
		UPDATE GameSeat gs
		SET gs.status = :available, gs.holdExpiresAt = null
		WHERE gs.status = :held
		AND EXISTS (
		    SELECT oi.id
		    FROM OrderItem oi
		    WHERE oi.gameSeat = gs
		    AND oi.order.status = :orderExpired
		    AND oi.order.paymentDeadline <= :now
		)
		""")
	int releaseGameSeatsByExpiredOrders(
		@Param("now")
		LocalDateTime now,
		@Param("held")
		GameSeatStatus held,
		@Param("orderExpired")
		OrderStatus orderExpired,
		@Param("available")
		GameSeatStatus available);
}
