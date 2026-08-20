package com.backtoback.reseat.domain.order.repository;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByReservation_Id(Long reservationId);

    /**
     * CREATED 상태이면서 결제 기한이 지난 주문을 EXPIRED로 벌크 전이한다.
     * <p>상태와 결제 기한 조건을 UPDATE의 WHERE 절에서 함께 확인해
     * 만료 대상 주문만 원자적으로 변경한다.</p>
     *
     * @param now 만료 판정 기준 시간
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

    /**
     * 결제 기한이 지나지 않은 CREATED 주문을 PAID로 전이한다.
     * <p>주문 ID, 상태와 결제 기한을 UPDATE의 WHERE 절에서 함께 확인해
     * 결제 완료 가능한 주문만 원자적으로 변경한다.</p>
     *
     * @param orderId 결제 완료 처리할 주문 ID
     * @param now 결제 기한 판정 기준 시간
     * @param created 결제 완료 처리 대상 상태
     * @param paid 변경할 결제 완료 상태
     * @return PAID로 전이된 주문 수
     */
    @Modifying
    @Query("""
        UPDATE Order o
        SET o.status = :paid
        WHERE o.id = :orderId
        AND o.status = :created
        AND o.paymentDeadline > :now
        """)
    int completeCreatedOrder(
        @Param("orderId") Long orderId,
        @Param("now") LocalDateTime now,
        @Param("created") OrderStatus created,
        @Param("paid") OrderStatus paid
    );
}
