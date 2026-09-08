package com.backtoback.reseat.domain.queue.dto.response;

import com.backtoback.reseat.domain.queue.entity.QueueEntryRejectionReason;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Consumer에서 거절된 대기열 진입 요청을 SSE reject 이벤트로 전달하는 응답 DTO.
 */
@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "대기열 진입 거절 SSE 이벤트 응답")
public class QueueEntryRejectionEventResponse {

    @Schema(
        description = "대기열 진입 거절 여부",
        example = "true"
    )
    private final boolean rejected;

    @Schema(
        description = "Consumer 대기열 진입 거절 사유",
        example = "WAITING_IN_OTHER_GAME"
    )
    private final QueueEntryRejectionReason reason;
}
