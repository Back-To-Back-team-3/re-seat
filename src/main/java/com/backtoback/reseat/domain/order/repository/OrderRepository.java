package com.backtoback.reseat.domain.order.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByReservation_Id(Long reservationId);

    /**
     * CREATED 상태이면서 결제 기한이 지난 주문을 EXPIRED로 벌크 전이한다.
     *
     * <p>상태와 결제 기한 조건을 UPDATE의 WHERE 절에서 함께 확인해
     * 만료 대상 주문만 원자적으로 변경한다.</p>
     *
     * @param now     만료 판정 기준 시간
     * @param created 만료 처리 대상 상태
     * @param expired 변경할 만료 상태
     * @return EXPIRED로 전이된 주문 수
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Order o
        SET o.status = :expired
        WHERE o.status = :created
        AND o.paymentDeadline <= :now
        """)
    int expireCreatedOrders(
        @Param("now") LocalDateTime now,
        @Param("created") OrderStatus created,
        @Param("expired") OrderStatus expired
    );
}
