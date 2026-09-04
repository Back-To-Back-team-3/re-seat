package com.backtoback.reseat.domain.order.entity;

/**
 * 주문 항목의 취소 상태
 */
public enum OrderItemStatus {
    ACTIVE, // 취소되지 않은 주문 항목
    CANCELED, // 환불 완료로 취소된 주문 항목
}
