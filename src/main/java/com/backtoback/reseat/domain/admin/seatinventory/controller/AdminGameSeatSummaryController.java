package com.backtoback.reseat.domain.admin.seatinventory.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.seatinventory.dto.SeatInventorySummaryResponse;
import com.backtoback.reseat.domain.seatinventory.service.SeatQueryService;
import com.backtoback.reseat.global.common.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 전용 경기 좌석 상태 요약 조회 API.
 * <p>
 * 좌석 조회 로직을 이미 갖고 있는 {@link SeatQueryService}를 그대로 확장해서 쓴다.
 * 관리자 전용 화면이라도 "재고 조회" 책임은 도메인 서비스에 있으므로, domain/admin에 별도 서비스를 새로 만들지 않는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/games")
public class AdminGameSeatSummaryController implements AdminGameSeatSummaryControllerDocs {

    private final SeatQueryService seatQueryService;

    /**
     * 경기 좌석 상태 요약 조회.
     *
     * @param gameId 경기 ID
     * @return 200 OK + 상태별 합계
     */
    @Override
    @GetMapping("/{gameId}/seats/summary")
    public ResponseEntity<ApiResponse<SeatInventorySummaryResponse>> summary(@PathVariable Long gameId) {
        return ResponseEntity.ok(ApiResponse.success("좌석 재고 요약 조회", seatQueryService.summarize(gameId)));
    }
}
