package com.backtoback.reseat.domain.reservation.entity;

public enum ReservationStatus {
    HOLDING, // 좌석 선점 중 (TTL 이내, 결제 대기)
    CONFIRMED, // 결제 승인 완료
    CANCELED, // 사용자 취소 또는 결제 실패 — L 1개(Game의 CANCELLED와 다름)
    EXPIRED // TTL 만료 (스케줄러 자동 전환)
}
