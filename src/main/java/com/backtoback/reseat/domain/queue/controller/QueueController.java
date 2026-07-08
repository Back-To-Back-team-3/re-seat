package com.backtoback.reseat.domain.queue.controller;

import com.backtoback.reseat.domain.queue.dto.response.QueueCancelResponse;
import com.backtoback.reseat.domain.queue.dto.response.QueueEnterResponse;
import com.backtoback.reseat.domain.queue.dto.response.QueueStatusResponse;
import com.backtoback.reseat.domain.queue.service.AdmissionTokenService;
import com.backtoback.reseat.domain.queue.service.QueueService;
import com.backtoback.reseat.domain.queue.service.SseService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/queues")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;
    private final SseService sseService;
    private final AdmissionTokenService admissionTokenService;

    /**
     * 3.1 대기열 진입 / 토큰 발급
     *
     * @param gameId 경기별 Game ID
     * @param userDetails JWT 인증
     * @return 입장 허용 또는 대기열 진입 정보
     */
    @PostMapping("/{gameId}/enter")
    public ResponseEntity<ApiResponse<QueueEnterResponse>> myQueueEnter(
            @PathVariable Long gameId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        Long userId = userDetails.getId();
        QueueEnterResponse response = queueService.myQueueEnter(gameId, userId);
        String message = response.isAdmitted() ? "입장 허용" : "대기열 진입";

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(message, response));
    }

    /**
     * 3.2 내 대기 상태 조회
     *
     * @param gameId 경기별 Game ID
     * @param userDetails JWT 인증
     * @return 현재 대기 순번 및 입장 허용 상태
     */
    @GetMapping("/{gameId}/me")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getMyQueueStatus(
            @PathVariable Long gameId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        Long userId = userDetails.getId();
        QueueStatusResponse response = queueService.getMyQueueStatus(gameId, userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("대기 상태 조회", response));
    }

    /**
     * 3.3 대기 순번 실시간 스트림 (SSE)
     *
     * @param gameId 경기별 Game ID
     * @param userDetails JWT 인증
     * @return SSE 이벤트 연결
     */
    @GetMapping(value = "/{gameId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMyQueue(
            @PathVariable Long gameId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
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
    @DeleteMapping("/{gameId}/me")
    public ResponseEntity<ApiResponse<QueueCancelResponse>> cancelMyQueue(
            @PathVariable Long gameId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        Long userId = userDetails.getId();
        QueueCancelResponse response = queueService.cancelMyQueue(gameId, userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("대기열 취소", response));
    }


    /**
     * MVP용 입장 허용 처리
     *
     * Redis 대기열 앞 사용자를 지정된 수만큼 입장 허용 상태로 전환하고 토큰 발급
     *
     * @param gameId 경기별 Game ID
     * @param limit 입장 허용 처리할 사용자 수
     * @return 입장 허용 처리된 사용자 수
     */
    @PostMapping("/{gameId}/admit")
    public ResponseEntity<ApiResponse<Integer>> admit(
            @PathVariable Long gameId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        int admittedCount = admissionTokenService.admit(gameId, limit);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(limit + "명 입장 허용 처리", admittedCount));
    }
}
