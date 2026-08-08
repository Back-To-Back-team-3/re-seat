package com.backtoback.reseat.domain.order.dto.response;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 응답 DTO
 *
 * <p>주문 기본 정보, 결제 기한, 선점 만료 시간, 주문 좌석 항목을 반환한다.</p>
 */
@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "주문 생성 · 조회 응답")
public class OrderResponse {

    @Schema(description = "주문 ID", example = "7001")
    private final Long orderId;

    @Schema(description = "주문 번호", example = "ORD-20260711-ABC123")
    private final String orderNo;

    @Schema(description = "총 주문 금액", example = "18000")
    private final int totalAmount;

    @Schema(description = "주문 상태", example = "CREATED")
    private final OrderStatus status;

    @Schema(description = "결제 기한", example = "2026-07-11 14:29:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime paymentDeadline;

    @Schema(description = "선점 만료 시간", example = "2026-07-11 14:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime holdExpiresAt;

    @Schema(description = "주문 좌석 항목 목록")
    private final List<OrderItemResponse> orderItems;

    /**
     * Order Entity와 주문 항목 목록을 주문 응답 DTO로 변환한다.
     *
     * @param order         주문 Entity
     * @param orderItems    저장된 주문 항목 목록
     * @param holdExpiresAt 선점 만료 시간
     * @return 주문 응답 DTO
     */
    public static OrderResponse from(
        Order order,
        List<OrderItem> orderItems,
        LocalDateTime holdExpiresAt
    ) {
        return OrderResponse.builder()
            .orderId(order.getId())
            .orderNo(order.getOrderNo())
            .totalAmount(order.getTotalAmount())
            .status(order.getStatus())
            .paymentDeadline(order.getPaymentDeadline())
            .holdExpiresAt(holdExpiresAt)
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
    @Schema(description = "주문 좌석 항목 응답")
    public static class OrderItemResponse {

        @Schema(description = "주문 항목 ID", example = "9001")
        private final Long orderItemId;

        @Schema(description = "경기 좌석 ID", example = "5001")
        private final Long gameSeatId;

        @Schema(description = "주문 확정 가격", example = "18000")
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
