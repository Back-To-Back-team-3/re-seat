package com.backtoback.reseat.domain.seatinventory.entity;

public enum GameSeatStatus {
    AVAILABLE,  // 판매 가능 (생성 시 기본값 / 선점 만료 복귀)
    HELD,       // 임시 선점 (hold_expires_at 이내)
    SOLD,       // 판매 완료 (결제 승인)
    BLOCKED     // 관리자 수동 차단
}
