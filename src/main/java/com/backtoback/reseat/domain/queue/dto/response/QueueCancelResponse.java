package com.backtoback.reseat.domain.queue.dto.response;

import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "대기열 취소 응답")
public class QueueCancelResponse {

	@Schema(
	    description = "경기 ID",
	    example = "10"
	)
	private final Long gameId;

	@Schema(
	    description = "대기열 상태",
	    example = "CANCELED"
	)
	private final QueueEntryHistoryStatus queueStatus;
}
