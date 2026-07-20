package com.backtoback.reseat.domain.queue.entity;

/**
 * 입장 토큰의 처리 상태
 */
public enum AdmissionTokenStatus {
    ACTIVE,     // 발급 후 유효 시간이 남이 있는 토큰 상태
    USED,       // 입장 검증을 거쳐 사용이 완료된 토큰 상태
    EXPIRED     // 유효 시간이 지나 더 이상 사용할 수 없는 토큰 상태
}
