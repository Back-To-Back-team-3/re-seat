package com.backtoback.reseat.domain.admin.seatinventory.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.seatinventory.dto.SeatStatusResponse;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.service.SeatQueryService;
import com.backtoback.reseat.global.common.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 전용 좌석 그리드 조회 API.
 * <p>
 * 공개 API({@code GET /api/v1/games/{gameId}/seats})는 JWT + Queue-Token을 모두 요구해
 * 대기열을 거치지 않는 관리자가 그대로 호출할 수 없다.
 * {@link SeatQueryService#getSeats} 자체에는 Queue-Token 관련 로직이 없으므로,
 * 이 컨트롤러가 같은 서비스 메서드를 ADMIN 권한 전용 경로로 한 번 더 노출한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/games")
public class AdminGameSeatGridController implements AdminGameSeatGridControllerDocs {

    private final SeatQueryService seatQueryService;

    /**
     * 경기 좌석 그리드 조회.
     * <p>grade 필터는 관리자 화면 와이어프레임에 없어 노출하지 않는다.
     *
     * @param gameId 경기 ID
     * @param zoneId 구역 필터 (선택)
     * @param status 상태 필터 (선택)
     * @return 200 OK + 좌석 현황 목록
     */
    @Override
    @GetMapping("/{gameId}/seats")
    public ResponseEntity<ApiResponse<List<SeatStatusResponse>>> getSeatGrid(
        @PathVariable Long gameId,
        @RequestParam(required = false) Long zoneId,
        @RequestParam(required = false) GameSeatStatus status
    ) {
        return ResponseEntity
            .ok(ApiResponse.success("좌석 그리드 조회", seatQueryService.getSeats(gameId, zoneId, null, status)));
    }
}
