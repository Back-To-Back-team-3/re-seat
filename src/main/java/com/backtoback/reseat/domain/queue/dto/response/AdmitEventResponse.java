package com.backtoback.reseat.domain.queue.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * 대기열을 통과한 사용자에게 SSE admit 이벤트로 전달하는 입장 정보 DTO
 */
@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AdmitEventResponse {
    private final boolean admitted;
    private final String queueToken;
    private final LocalDateTime tokenExpiresAt;
}
