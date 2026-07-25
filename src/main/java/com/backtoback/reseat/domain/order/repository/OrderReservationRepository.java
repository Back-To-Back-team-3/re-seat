package com.backtoback.reseat.domain.order.repository;

import com.backtoback.reseat.domain.reservation.entity.Reservation;
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
 * 주문 생성 과정에서 Reservation 잠금 조회와 선점 만료 시간 갱신을 담당하는 Repository
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
}
