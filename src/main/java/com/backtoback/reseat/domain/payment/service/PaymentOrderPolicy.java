package com.backtoback.reseat.domain.payment.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.exception.OrderExpiredException;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.exception.PaymentAccessDeniedException;
import com.backtoback.reseat.domain.payment.exception.PaymentOrderNotFoundException;
import com.backtoback.reseat.domain.payment.exception.PaymentOrderNotPayableException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentOrderPolicy {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /**
     * 주문을 조회하고 결제 요청 사용자에게 소유권이 있는지 검증한다.
     */
    public Order getOwnedOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(PaymentOrderNotFoundException::new);

        if (!order.getUser().getId().equals(userId)) {
            throw new PaymentAccessDeniedException();
        }

        return order;
    }

    /**
     * 주문 결제 기한과 상태를 검증하고, 기한이 지났다면 READY 결제를 실패 처리한다.
     */
    public void ensurePayable(Payment payment, Order order) {
        LocalDateTime now = LocalDateTime.now();
        if (!order.getPaymentDeadline().isAfter(now)) {
            if (payment != null) {
                payment.fail("주문 결제 기한이 만료되었습니다.", now);
            }
            orderService.expireOrder(order.getId());
            throw new OrderExpiredException();
        }

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new PaymentOrderNotPayableException();
        }
    }
}
