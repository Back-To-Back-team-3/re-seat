package com.backtoback.reseat.domain.queue.entity;

public enum AdmissionTokenStatus {
    ACTIVE,     // 사용 가능한 입장 토큰
    USED,       // 사용 완료된 입장 토큰
    EXPIRED     // 만료된 입장 토큰
}
