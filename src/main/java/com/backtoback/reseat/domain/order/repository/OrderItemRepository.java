package com.backtoback.reseat.domain.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.entity.OrderItemStatus;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder_Id(Long orderId);

    /**
     * 같은 주문에 지정한 상태의 주문 항목이 남아 있는지 확인한다.
     *
     * @param orderId 주문 ID
     * @param status 확인할 주문 항목 상태
     * @return 해당 상태의 주문 항목이 존재하면 true, 없으면 false
     */
    boolean existsByOrder_IdAndStatus(Long orderId, OrderItemStatus status);
}
