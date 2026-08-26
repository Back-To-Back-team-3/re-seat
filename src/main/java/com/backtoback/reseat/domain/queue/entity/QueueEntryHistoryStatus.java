package com.backtoback.reseat.domain.queue.entity;

/**
 * 대기열 진입 이력의 처리 상태
 */
public enum QueueEntryHistoryStatus {
    WAITING, // Redis 대기열에서 입장 순서를 기다리는 상태
    ADMITTED, // 대기열을 통과하고 입장 토큰이 발급된 상태
    CANCELED // 대기 취소 또는 연결 종료 유예시간 만료로 대기열에서 이탈한 상태
}
