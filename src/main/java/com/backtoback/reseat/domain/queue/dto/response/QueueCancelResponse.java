package com.backtoback.reseat.domain.queue.dto.response;

import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class QueueCancelResponse {
    private final Long gameId;
    private final QueueEntryHistoryStatus queueStatus;
}
