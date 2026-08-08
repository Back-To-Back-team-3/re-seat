package com.backtoback.reseat.domain.seatinventory.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.seatinventory.dto.GameSeatOpenResponse;
import com.backtoback.reseat.domain.seatinventory.service.GameSeatCreateService;
import com.backtoback.reseat.global.common.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자용 경기 좌석 재고 API.
 *
 * <p>같은 규칙이 두 곳에 존재하면 한쪽만 수정되는 사고가 난다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/games")
public class AdminGameSeatController implements AdminGameSeatControllerDocs {

    private final GameSeatCreateService gameSeatCreateService;

    /**
     * 경기 좌석 재고 오픈.
     *
     * @param gameId 재고를 오픈할 경기 ID
     * @return 201 Created + 생성 건수·가격 범위
     */
    @Override
    @PostMapping("/{gameId}/seats")
    public ResponseEntity<ApiResponse<GameSeatOpenResponse>> openSeatInventory(
        @PathVariable Long gameId
    ) {
        GameSeatOpenResponse response = gameSeatCreateService.openInventory(gameId);

        // 리소스가 새로 생성됐으므로 201.
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("좌석 재고 오픈 성공", response));
    }
}
