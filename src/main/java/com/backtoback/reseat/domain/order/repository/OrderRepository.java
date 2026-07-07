package com.backtoback.reseat.domain.order.repository;

import com.backtoback.reseat.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
