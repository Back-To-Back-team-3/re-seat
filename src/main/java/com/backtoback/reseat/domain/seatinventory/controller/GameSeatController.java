package com.backtoback.reseat.domain.seatinventory.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.queue.service.AdmissionTokenService;
import com.backtoback.reseat.domain.seatinventory.dto.SeatStatusResponse;
import com.backtoback.reseat.domain.seatinventory.dto.ZoneSummaryResponse;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.service.SeatQueryService;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 경기 좌석 현황·구역 요약 조회 API.
 * <p>프론트 좌석 배치도 UI의 데이터 소스.
 * 성공 응답은 팀 컨벤션에 따라 {@code ResponseEntity<ApiResponse<T>>}로 반환한다.
 * 에러 상태 코드는 GlobalExceptionHandler가 일괄 매핑한다.
 * <p>인가: JWT 인증 + Queue-Token 검증.
 * Queue-Token은 대기열 통과 사용자임을 보장하며, getSeats에서는 조회(validateToken)만 수행한다.
 * 토큰 소비는 두 경로로 나뉜다:
 * 결제 승인·실패 시 PaymentService·PaymentApprovalService가 consumeToken(...)을 호출하고,
 * 승인 복구 작업이 성공한 경우 PaymentRecoveryService가 finalizeTokenAfterPayment(...)를 호출한다.
 * 부분 취소 복구와 빈 토큰은 이 복구 호출 대상에서 제외된다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/games")
public class GameSeatController implements GameSeatControllerDocs {

    private final SeatQueryService seatQueryService;
    private final AdmissionTokenService admissionTokenService;

    /**
     * 경기의 좌석 현황을 조회한다.
     * <p>필터를 지정하지 않으면 전체 500건을 반환한다.
     * 좌석 배치도 렌더링 시 zoneId 필터로 구역 단위 조회를 권장한다.
     *
     * @param gameId 경기 ID
     * @param zoneId 구역 ID (선택)
     * @param grade 좌석 등급 (선택, INFIELD/OUTFIELD)
     * @param status 좌석 상태 (선택, AVAILABLE/HELD/SOLD/BLOCKED)
     * @return 좌석 현황 목록
     */
    // getSeats: 200 응답 래핑
    @Override
    @GetMapping("/{gameId}/seats")
    public ResponseEntity<ApiResponse<List<SeatStatusResponse>>> getSeats(
        @PathVariable Long gameId,
        @RequestParam(required = false) Long zoneId,
        @RequestParam(required = false) SeatGrade grade,
        @RequestParam(required = false) GameSeatStatus status,
        @RequestHeader(
            value = "Queue-Token",
            required = false
        ) String queueToken,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        // getSeats는 validateToken(조회)만 수행한다.
        // 토큰 소비는 결제 승인·실패 시 consumeToken(PaymentService·PaymentApprovalService),
        // 승인 복구 성공 시 finalizeTokenAfterPayment(PaymentRecoveryService)로 나뉘어 호출된다.
        // 부분 취소 복구·빈 토큰은 이 호출 대상에서 제외된다.
        admissionTokenService.validateToken(userDetails.getId(), gameId, queueToken);

        List<SeatStatusResponse> seats = seatQueryService.getSeats(gameId, zoneId, grade, status);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("좌석 현황 조회 성공", seats));
    }

    /**
     * 경기의 구역별 잔여 좌석 수를 조회합니다.
     *
     * @param gameId 경기 ID
     * @return 구역 요약 목록 (잔여수 0인 구역 포함)
     */
    // getZoneSummaries: 200 응답 래핑
    @Override
    @GetMapping("/{gameId}/zones")
    public ResponseEntity<ApiResponse<List<ZoneSummaryResponse>>> getZoneSummaries(@PathVariable Long gameId) {
        List<ZoneSummaryResponse> zones = seatQueryService.getZoneSummaries(gameId);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("구역 요약 조회 성공", zones));
    }
}
