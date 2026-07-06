package com.backtoback.reseat.domain.game.entity;

public enum BookingStatus {
    SCHEDULED,  // 예매 오픈 전 (등록 시 기본값)
    OPEN,       // 예매 가능
    CLOSED,     // 예매 마감
    CANCELLED   // 경기 취소 (우천 등) — L 두 개(CANCELLED)에 주의
}
