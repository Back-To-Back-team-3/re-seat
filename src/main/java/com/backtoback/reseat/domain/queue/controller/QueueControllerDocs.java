package com.backtoback.reseat.domain.queue.controller;

import java.util.concurrent.CompletionStage;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.backtoback.reseat.domain.queue.dto.response.QueueCancelResponse;
import com.backtoback.reseat.domain.queue.dto.response.QueueStatusResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue", description = "대기열 진입 · 상태 조회 · SSE · 취소 API")
public interface QueueControllerDocs {

	@Operation(summary = "대기열 진입 요청", description = "경기 대기열 진입 요청을 비동기로 접수하며, 처리 완료 전에는 대기 상태가 조회되지 않을 수 있습니다.", security = @SecurityRequirement(name = "JWT Bearer Token"))
	@ApiResponses({
		@ApiResponse(responseCode = "202", description = "대기열 진입 요청 접수"),
		@ApiResponse(responseCode = "400", description = "INVALID_REQUEST", content = @Content),
		@ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
		@ApiResponse(responseCode = "404", description = "GAME_NOT_FOUND / USER_NOT_FOUND", content = @Content),
		@ApiResponse(responseCode = "503", description = "QUEUE_EVENT_PUBLISH_FAILED", content = @Content)
	})
	CompletionStage<ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<Void>>> requestQueueEntry(
		@Parameter(description = "경기 ID", example = "10", required = true)
		Long gameId,
		@Parameter(hidden = true)
		CustomUserDetails userDetails);

	@Operation(summary = "내 대기 상태 조회", description = "현재 대기 순번, 예상 대기시간과 입장 허용 여부를 조회합니다.", security = @SecurityRequirement(name = "JWT Bearer Token"))
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "대기 상태 조회", content = @Content(examples = {
			@ExampleObject(name = "응답(200/대기 중)", value = """
				{
				    "success": true,
				    "errorCode": null,
				    "message": "대기 상태 조회",
				    "data": {
				        "rank": 10,
				        "estimatedWaitSeconds": 3,
				        "queueStatus": "WAITING",
				        "admitted": false
				    }
				}
				"""),
			@ExampleObject(name = "응답(200/입장 허용)", value = """
				{
				    "success": true,
				    "errorCode": null,
				    "message": "대기 상태 조회",
				    "data": {
				        "rank": 0,
				        "estimatedWaitSeconds": 0,
				        "queueStatus": "ADMITTED",
				        "admitted": true
				    }
				}
				""")
		})),
		@ApiResponse(responseCode = "400", description = "INVALID_REQUEST", content = @Content),
		@ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
		@ApiResponse(responseCode = "404", description = "QUEUE_ENTRY_NOT_FOUND", content = @Content)
	})
	ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<QueueStatusResponse>> getMyQueueStatus(
		@Parameter(description = "경기 ID", example = "10", required = true)
		Long gameId,
		@Parameter(hidden = true)
		CustomUserDetails userDetails);

	@Operation(summary = "대기 순번 실시간 스트림 (SSE)", description = "SSE로 대기 상태와 입장 허용 정보를 전송하며, 대기중 마지막 연결이 종료되면 60초 동안 재연결을 허용합니다.", security = @SecurityRequirement(name = "JWT Bearer Token"))
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "SSE 연결 성공", content = @Content(mediaType = "text/event-stream", examples = {
			@ExampleObject(name = "rank 이벤트(대기 중)", value = """
				event: rank
				data: {"rank": 10, "estimatedWaitSeconds": 3, "queueStatus": "WAITING", "admitted": false}
				"""),
			@ExampleObject(name = "rank 이벤트(입장 허용)", value = """
				event: rank
				data: {"rank": 0, "estimatedWaitSeconds": 0, "queueStatus": "ADMITTED", "admitted": true}
				"""),
			@ExampleObject(name = "admit 이벤트", value = """
				event: admit
				data: {"admitted": true, "queueToken": "qt_c6f443cf-a0d7-467f-b93f-da417c135a97", "tokenExpiresAt": "2026-07-21T21:50:00"}
				""")
		})),
		@ApiResponse(responseCode = "401", description = "인증 실패", content = @Content)
	})
	SseEmitter streamMyQueue(
		@Parameter(description = "경기 ID", example = "10", required = true)
		Long gameId,
		@Parameter(hidden = true)
		CustomUserDetails userDetails);

	@Operation(summary = "대기열 취소", description = "현재 대기열 진입을 취소하고 활성 Queue-Token이 있으면 함께 무효화합니다.", security = @SecurityRequirement(name = "JWT Bearer Token"))
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "대기열 취소"),
		@ApiResponse(responseCode = "400", description = "INVALID_REQUEST", content = @Content),
		@ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
		@ApiResponse(responseCode = "404", description = "QUEUE_ENTRY_NOT_FOUND / USER_NOT_FOUND", content = @Content),
		@ApiResponse(responseCode = "409", description = "QUEUE_INVALID_STATUS", content = @Content)
	})
	ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<QueueCancelResponse>> cancelMyQueue(
		@Parameter(description = "경기 ID", example = "10", required = true)
		Long gameId,
		@Parameter(hidden = true)
		CustomUserDetails userDetails);
}
