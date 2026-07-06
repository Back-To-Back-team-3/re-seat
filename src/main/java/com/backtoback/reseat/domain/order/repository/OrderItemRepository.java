package com.backtoback.reseat.domain.order.repository;

import com.backtoback.reseat.domain.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
