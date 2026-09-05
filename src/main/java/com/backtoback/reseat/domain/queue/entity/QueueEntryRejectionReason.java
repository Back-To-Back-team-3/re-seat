package com.backtoback.reseat.domain.queue.entity;

/**
 * Consumer가 대기열 진입을 완료하지 않은 사용자 정책상의 거절 사유.
 * <p>Redis에는 Enum 이름을 문자열로 저장하고
 * 후속 SSE 거절 이벤트의 사유 코드로 그대로 전달한다.</p>
 */
public enum QueueEntryRejectionReason {
    WAITING_IN_OTHER_GAME,              // 다른 경기 대기열에서 대기 중
    ACTIVE_QUEUE_TOKEN_IN_ANOTHER_GAME, // 다른 경기의 활성 Queue-Token 보유
    BOOKING_NOT_OPEN,                   // Consumer 처리 시점에 예매 미오픈
}
