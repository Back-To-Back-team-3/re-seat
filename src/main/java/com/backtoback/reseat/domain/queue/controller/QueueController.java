package com.backtoback.reseat.domain.queue.controller;

import java.util.concurrent.CompletionStage;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.backtoback.reseat.domain.queue.dto.response.QueueCancelResponse;
import com.backtoback.reseat.domain.queue.dto.response.QueueStatusResponse;
import com.backtoback.reseat.domain.queue.service.QueueService;
import com.backtoback.reseat.domain.queue.service.SseService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 대기열 API Controller
 * <p>대기열 진입, 상태 조회, SSE 순번 스트림, 취소 API를 제공한다.</p>
 */
@RestController
@RequestMapping("/api/v1/queues")
@RequiredArgsConstructor
public class QueueController implements QueueControllerDocs {

	private final QueueService queueService;
	private final SseService sseService;

	/**
	 * 3.1 대기열 진입 요청
	 *
	 * @param gameId 경기별 Game ID
	 * @param userDetails JWT 인증
	 * @return 대기열 진입 요청 접수 결과
	 */
	@Override
	@PostMapping("/{gameId}/enter")
	public CompletionStage<ResponseEntity<ApiResponse<Void>>> requestQueueEntry(
	    @PathVariable Long gameId,
	    @AuthenticationPrincipal CustomUserDetails userDetails
	) {

		Long userId = userDetails.getId();

		// Kafka 진입 이벤트 발행이 성공하면 요청 접수 의미로 202 Accepted를 반환한다.
		// Consumer가 비동기로 처리하므로 이 시점에서 DB 이력과 Redis 대기열 등록이 완료되지 않을 수 있다.
		return queueService
		    .requestQueueEntry(gameId, userId)
		    .thenApply(
		        ignored -> ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success("대기열 진입 요청 접수", null))
		    );
	}

	/**
	 * 3.2 내 대기 상태 조회
	 *
	 * @param gameId 경기별 Game ID
	 * @param userDetails JWT 인증
	 * @return 현재 대기 순번 및 입장 허용 상태
	 */
	@Override
	@GetMapping("/{gameId}/me")
	public ResponseEntity<ApiResponse<QueueStatusResponse>> getMyQueueStatus(
	    @PathVariable Long gameId,
	    @AuthenticationPrincipal CustomUserDetails userDetails
	) {

		Long userId = userDetails.getId();
		QueueStatusResponse response = queueService.getMyQueueStatus(gameId, userId);

		return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("대기 상태 조회", response));
	}

	/**
	 * 3.3 대기 순번 실시간 스트림 (SSE)
	 *
	 * @param gameId 경기별 Game ID
	 * @param userDetails JWT 인증
	 * @return SSE 이벤트 연결
	 */
	@Override
	@GetMapping(
	    value = "/{gameId}/stream",
	    produces = MediaType.TEXT_EVENT_STREAM_VALUE
	)
	public SseEmitter streamMyQueue(@PathVariable Long gameId, @AuthenticationPrincipal CustomUserDetails userDetails) {
		Long userId = userDetails.getId();
		return sseService.streamMyQueue(gameId, userId);
	}

	/**
	 * 3.4 대기열 취소
	 *
	 * @param gameId 경기별 Game ID
	 * @param userDetails JWT 인증
	 * @return 대기열 취소 정보
	 */
	@Override
	@DeleteMapping("/{gameId}/me")
	public ResponseEntity<ApiResponse<QueueCancelResponse>> cancelMyQueue(
	    @PathVariable Long gameId,
	    @AuthenticationPrincipal CustomUserDetails userDetails
	) {

		Long userId = userDetails.getId();
		QueueCancelResponse response = queueService.cancelMyQueue(gameId, userId);

		return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("대기열 취소", response));
	}
}
