package com.backtoback.reseat.domain.order.dto.response;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 주문 취소 응답 DTO
 * <p>취소된 주문 ID와 변경된 주문 상태를 반환한다.</p>
 */
@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "주문 취소 응답")
public class OrderCancelResponse {

    @Schema(
        description = "주문 ID",
        example = "7001"
    )
    private final Long orderId;

    @Schema(
        description = "취소 후 주문 상태",
        example = "CANCELED"
    )
    private final OrderStatus status;

    /**
     * Order Entity를 주문 취소 응답 DTO로 변환한다.
     *
     * @param order 취소된 주문 Entity
     * @return 주문 취소 응답 DTO
     */
    public static OrderCancelResponse from(Order order) {
        return OrderCancelResponse.builder().orderId(order.getId()).status(order.getStatus()).build();
    }
}
