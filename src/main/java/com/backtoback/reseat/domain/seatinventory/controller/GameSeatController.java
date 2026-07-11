package com.backtoback.reseat.domain.seatinventory.controller;

import com.backtoback.reseat.domain.seatinventory.dto.SeatStatusResponse;
import com.backtoback.reseat.domain.seatinventory.dto.ZoneSummaryResponse;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.service.SeatQueryService;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경기 좌석 현황·구역 요약 조회 API.
 *
 * <p>프론트 좌석 배치도 UI의 데이터 소스.
 * 200 OK만 반환하므로 ResponseEntity 없이 ApiResponse<T>를 직접 반환한다.
 *
 * <p>인가: 인증된 사용자 전체 접근 가능.
 * Queue-Token 검증은 B-3 계약 확정 후 추가 예정 (현재 stub).
 */
@Tag(name = "Game Seat", description = "경기 좌석 현황·구역 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/games")
public class GameSeatController {

    private final SeatQueryService seatQueryService;

    /**
     * 경기의 좌석 현황을 조회합니다.
     *
     * <p>필터를 지정하지 않으면 전체 500건을 반환합니다.
     * 좌석 배치도 렌더링 시 zoneId 필터로 구역 단위 조회를 권장합니다.
     *
     * @param gameId 경기 ID
     * @param zoneId 구역 ID (선택)
     * @param grade  좌석 등급 (선택, INFIELD/OUTFIELD)
     * @param status 좌석 상태 (선택, AVAILABLE/HELD/SOLD/BLOCKED)
     * @return 좌석 현황 목록
     */
    @Operation(
        summary = "경기 좌석 현황 조회",
        description = """
                    경기의 좌석 현황을 조회한다. 필터 미지정 시 전체 500건 반환.
                    재고 미오픈 경기 조회 시 409 SEAT_INVENTORY_NOT_OPENED.

                    Queue-Token 검증: B-3 대기열 토큰 계약 확정 후 추가 예정.
                    """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "GAME_NOT_FOUND",
            content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "SEAT_INVENTORY_NOT_OPENED",
            content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/{gameId}/seats")
    public ApiResponse<List<SeatStatusResponse>> getSeats(
        @Parameter(description = "경기 ID", example = "1146")
        @PathVariable Long gameId,

        @Parameter(description = "구역 ID (선택)")
        @RequestParam(required = false) Long zoneId,

        @Parameter(description = "좌석 등급 (선택)")
        @RequestParam(required = false) SeatGrade grade,

        @Parameter(description = "좌석 상태 (선택)")
        @RequestParam(required = false) GameSeatStatus status,

        // TODO: B-3 대기열 토큰 계약 확정 후 검증 로직 추가
        @Parameter(description = "대기열 통과 토큰 (Queue-Token)", required = false)
        @RequestParam(required = false) String queueToken
    ) {
        // TODO: queueToken 검증 (B-3 완료 후)
        List<SeatStatusResponse> seats = seatQueryService.getSeats(gameId, zoneId, grade, status);
        return ApiResponse.success(seats);
    }

    /**
     * 경기의 구역별 잔여 좌석 수를 조회합니다.
     *
     * @param gameId 경기 ID
     * @return 구역 요약 목록 (잔여수 0인 구역 포함)
     */
    @Operation(
        summary = "경기 구역별 잔여 좌석 조회",
        description = """
                    경기의 구역별 잔여 좌석 수를 집계해 반환한다.
                    잔여수가 0인 구역(매진)도 결과에 포함된다.
                    재고 미오픈 경기 조회 시 409 SEAT_INVENTORY_NOT_OPENED.
                    """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "UNAUTHORIZED",
            content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "GAME_NOT_FOUND",
            content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "SEAT_INVENTORY_NOT_OPENED",
            content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @GetMapping("/{gameId}/zones")
    public ApiResponse<List<ZoneSummaryResponse>> getZoneSummaries(
        @Parameter(description = "경기 ID", example = "1146")
        @PathVariable Long gameId
    ) {
        List<ZoneSummaryResponse> zones = seatQueryService.getZoneSummaries(gameId);
        return ApiResponse.success(zones);
    }
}
