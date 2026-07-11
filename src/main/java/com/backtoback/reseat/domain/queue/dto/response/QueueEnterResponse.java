package com.backtoback.reseat.domain.queue.dto.response;

import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class QueueEnterResponse {
    private final Long gameId;
    private final Long rank;
    private final Long estimatedWaitSeconds;
    private final QueueEntryHistoryStatus queueStatus;
    private final boolean admitted;
    private final String queueToken;
    private final LocalDateTime tokenExpiresAt;
}
