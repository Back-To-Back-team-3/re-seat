package com.backtoback.reseat.domain.queue.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 대기열을 통과한 사용자에게 SSE admit 이벤트로 전달하는 입장 정보 DTO
 */
@Getter
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "입장 허용 SSE 이벤트 응답")
public class AdmitEventResponse {

	@Schema(description = "입장 허용 여부", example = "true")
	private final boolean admitted;

	@Schema(description = "Queue-Token", example = "qt_c6f443cf-a0d7-467f-b93f-da417c135a97")
	private final String queueToken;

	@Schema(description = "Queue-Token 만료 시간", example = "2026-07-21T21:50:00")
	private final LocalDateTime tokenExpiresAt;
}
