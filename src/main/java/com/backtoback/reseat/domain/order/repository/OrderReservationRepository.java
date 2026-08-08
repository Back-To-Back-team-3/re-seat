package com.backtoback.reseat.domain.order.repository;

import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 주문 생성 과정에서 Reservation 잠금 · 선점 만료 시간 갱신과
 * 주문 만료에 따른 Reservation 상태 전이를 담당하는 Repository
 */
public interface OrderReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 주문 생성 대상 예약을 비관적 락으로 조회한다.
     *
     * <p>잠금은 주문 생성 트랜잭션이 끝날 때까지 유지된다.</p>
     *
     * <p>락 획득 대기 시간은 2초로 제한한다.</p>
     *
     * @param reservationId 조회할 예약 ID
     * @return 조회된 예약
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM Reservation r
        WHERE r.id = :reservationId
        """)
    @QueryHints(value = {
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")
    })
    Optional<Reservation> findByIdWithPessimisticWriteLock(@Param("reservationId") Long reservationId);

    /**
     * 주문 생성 과정에서 Reservation의 선점 만료 시간을 갱신한다.
     *
     * <p>예약 행에 대한 비관적 락을 획득하고 예약 상태와 선점 만료 여부를 검증한 뒤 호출한다.</p>
     *
     * @param reservationId 갱신할 예약 ID
     * @param holdExpiresAt 새로 적용할 선점 만료 시간
     */
    @Modifying
    @Query("""
        UPDATE Reservation r
        SET r.holdExpiresAt = :holdExpiresAt
        WHERE r.id = :reservationId
        """)
    void updateHoldExpiresAtById(
        @Param("reservationId") Long reservationId,
        @Param("holdExpiresAt") LocalDateTime holdExpiresAt
    );

    /**
     * 결제 기한 만료 주문과 연결된 HOLDING 예약을 EXPIRED로 벌크 전이한다.
     *
     * <p>EXPIRED 주문의 결제 기한을 함께 확인해
     * 주문 만료 처리 대상과 연결된 예약만 변경한다.</p>
     *
     * @param now                만료 판정 기준 시간
     * @param holding            만료 처리 대상 예약 상태
     * @param orderExpired       연결된 주문의 만료 상태
     * @param reservationExpired 변경할 예약 상태
     * @return EXPIRED로 전이된 예약 수
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Reservation r
        SET r.status = :reservationExpired
        WHERE r.status = :holding
        AND EXISTS (
            SELECT o.id
            FROM Order o
            WHERE o.reservation = r
            AND o.status = :orderExpired
            AND o.paymentDeadline <= :now
        )
        """)
    int expireReservationsByExpiredOrders(
        @Param("now") LocalDateTime now,
        @Param("holding") ReservationStatus holding,
        @Param("orderExpired") OrderStatus orderExpired,
        @Param("reservationExpired") ReservationStatus reservationExpired
    );
}
