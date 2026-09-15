package com.backtoback.reseat.domain.admin.seatinventory.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.admin.seatinventory.dto.request.SeatBlockRequest;
import com.backtoback.reseat.domain.admin.seatinventory.dto.response.GameSeatStatusResponse;
import com.backtoback.reseat.domain.admin.seatinventory.service.AdminGameSeatStatusService;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.global.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 전용 좌석 판매 차단·해제 API.
 * <p>
 * 리소스 루트가 {@code /admin/games}(재고 오픈)와 달리 {@code /admin/game-seats}이므로 별도 컨트롤러로 분리했다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/game-seats")
public class AdminGameSeatBlockController implements AdminGameSeatBlockControllerDocs {

    private final AdminGameSeatStatusService adminGameSeatStatusService;

    /**
     * 좌석 판매 차단 (AVAILABLE → BLOCKED).
     *
     * @param gameSeatId 차단할 경기 좌석 ID
     * @param request 차단 사유
     * @return 200 OK + 변경된 좌석 상태
     */
    @Override
    @PostMapping("/{gameSeatId}/block")
    public ResponseEntity<ApiResponse<GameSeatStatusResponse>> block(
        @PathVariable Long gameSeatId,
        @Valid @RequestBody SeatBlockRequest request
    ) {
        adminGameSeatStatusService.blockSeat(gameSeatId, request.reason());
        return ResponseEntity
            .ok(ApiResponse.success("좌석 판매 차단 완료", new GameSeatStatusResponse(gameSeatId, GameSeatStatus.BLOCKED)));
    }

    /**
     * 좌석 차단 해제 (BLOCKED → AVAILABLE).
     *
     * @param gameSeatId 해제할 경기 좌석 ID
     * @param request 해제 사유
     * @return 200 OK + 변경된 좌석 상태
     */
    @Override
    @PostMapping("/{gameSeatId}/unblock")
    public ResponseEntity<ApiResponse<GameSeatStatusResponse>> unblock(
        @PathVariable Long gameSeatId,
        @Valid @RequestBody SeatBlockRequest request
    ) {
        adminGameSeatStatusService.unblockSeat(gameSeatId, request.reason());
        return ResponseEntity
            .ok(ApiResponse.success("좌석 차단 해제 완료", new GameSeatStatusResponse(gameSeatId, GameSeatStatus.AVAILABLE)));
    }
}
