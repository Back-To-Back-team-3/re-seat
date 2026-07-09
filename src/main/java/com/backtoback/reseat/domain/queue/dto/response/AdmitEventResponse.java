package com.backtoback.reseat.domain.queue.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AdmitEventResponse {
    private final boolean admitted;
    private final String queueToken;
    private final LocalDateTime tokenExpiresAt;
}
