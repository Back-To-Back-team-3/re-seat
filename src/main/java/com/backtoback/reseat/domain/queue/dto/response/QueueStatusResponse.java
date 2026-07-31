package com.backtoback.reseat.domain.queue.dto.response;

import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "대기 상태 응답")
public class QueueStatusResponse {

    @Schema(description = "현재 대기 순번 (입장 허용 시 0)", example = "10")
    private final Long rank;

    @Schema(description = "예상 대기시간(초)", example = "3")
    private final Long estimatedWaitSeconds;

    @Schema(description = "대기열 상태 (WAITING 또는 ADMITTED)", example = "WAITING")
    private final QueueEntryHistoryStatus queueStatus;

    @Schema(description = "입장 허용 여부", example = "false")
    private final boolean admitted;
}
