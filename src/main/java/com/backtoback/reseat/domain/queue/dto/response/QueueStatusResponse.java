package com.backtoback.reseat.domain.queue.dto.response;

import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자의 현재 대기 순번과 입장 허용 여부를 전달하는 응답 DTO
 *
 * <p>대기열 상태 조회 API와 SSE rank 이벤트에서 사용한다.</p>
 */
@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class QueueStatusResponse {
    private final Long rank;
    private final Long estimatedWaitSeconds;
    private final QueueEntryHistoryStatus queueStatus;
    private final boolean admitted;
}
