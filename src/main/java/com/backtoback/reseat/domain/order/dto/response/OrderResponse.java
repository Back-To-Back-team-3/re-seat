package com.backtoback.reseat.domain.order.dto.response;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 응답 DTO
 *
 * <p>주문 기본 정보, 결제 제한 시간, 주문 좌석 항목을 반환한다.</p>
 */
@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderResponse {

    private final Long orderId;
    private final String orderNo;
    private final int totalAmount;
    private final OrderStatus status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime paymentDeadline;

    private final List<OrderItemResponse> orderItems;

    /**
     * Order Entity와 주문 항목 목록을 주문 응답 DTO로 변환한다.
     *
     * @param order 주문 Entity
     * @param orderItems 저장된 주문 항목 목록
     * @return 주문 응답 DTO
     */
    public static OrderResponse from(
            Order order,
            List<OrderItem> orderItems
    ) {
        return OrderResponse.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .paymentDeadline(order.getPaymentDeadline())
                .orderItems(orderItems.stream()
                        .map(OrderItemResponse::from)
                        .toList())
                .build();
    }

    /**
     * 주문 응답에 포함되는 좌석 단위 주문 항목 DTO
     */
    @Getter
    @Builder
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OrderItemResponse {

        private final Long orderItemId;
        private final Long gameSeatId;
        private final int price;

        /**
         * OrderItem Entity를 주문 항목 응답 DTO로 변환한다.
         *
         * @param orderItem 저장된 주문 항목 Entity
         * @return 주문 항목 응답 DTO
         */
        public static OrderItemResponse from(OrderItem orderItem) {
            return OrderItemResponse.builder()
                    .orderItemId(orderItem.getId())
                    .gameSeatId(orderItem.getGameSeat().getId())
                    .price(orderItem.getPrice())
                    .build();
        }
    }
}
