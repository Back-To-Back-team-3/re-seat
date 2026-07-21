package com.backtoback.reseat.domain.queue.dto.response;

import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자의 경기별 대기열 취소 결과를 전달하는 응답 DTO
 */
@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class QueueCancelResponse {
    private final Long gameId;
    private final QueueEntryHistoryStatus queueStatus;
}
