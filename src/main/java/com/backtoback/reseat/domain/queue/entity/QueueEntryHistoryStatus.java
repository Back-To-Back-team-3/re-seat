package com.backtoback.reseat.domain.queue.entity;

public enum QueueEntryHistoryStatus {
    WAITING,    // 대기 중
    ADMITTED,   // 예매 화면 입장 허용
    CANCELED    // 사용자 또는 시스템 취소
}
