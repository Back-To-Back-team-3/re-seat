package com.backtoback.reseat.domain.order.entity;

public enum OrderStatus {
    CREATED, // 주문 생성
    PAID, // 결제 완료
    CANCELED, // 주문 취소
    EXPIRED // 결제 제한 시간 만료
}
