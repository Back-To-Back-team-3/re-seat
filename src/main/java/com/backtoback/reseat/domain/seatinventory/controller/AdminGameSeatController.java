package com.backtoback.reseat.domain.seatinventory.controller;

import com.backtoback.reseat.domain.seatinventory.dto.GameSeatOpenResponse;
import com.backtoback.reseat.domain.seatinventory.service.GameSeatCreateService;
import com.backtoback.reseat.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자용 경기 좌석 재고 API.
 *
 * <p>같은 규칙이 두 곳에 존재하면 한쪽만 수정되는 사고가 난다.
 */
@Tag(name = "Admin - Game Seat", description = "경기 좌석 재고 관리 API (ROLE_ADMIN 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/games")
public class AdminGameSeatController {

    private final GameSeatCreateService gameSeatCreateService;

    /**
     * 경기 좌석 재고 오픈.
     * <p>
     * - ROLE_ADMIN 권한이 필요합니다.
     * - 구장의 활성 좌석 전체에 대해 PricePolicy가 산정한 가격을 부여하고
     *   AVAILABLE 상태의 재고를 생성한다. 경기당 1회만 호출할 수 있다.
     *
     * @param gameId 재고를 오픈할 경기 ID
     * @return 201 Created + 생성 건수·가격 범위
     */
    @Operation(
        summary = "경기 좌석 재고 오픈",
        description = """
                    ROLE_ADMIN 필요. 해당 경기의 판매 좌석 재고(game_seats)를 일괄 생성한다.

                    가격 산정식: 구역 성인 기본가(요일 반영) × 시기 배수
                      - 화~목: seat_zones.base_price (INFIELD 18,000 / OUTFIELD 16,000)
                      - 금~월: INFIELD 25,000 / OUTFIELD 23,000
                      - 9~10월: × 1.2

                    가격은 서버 정책이 결정한다. 요청 바디는 없다.
                    아동 할인·배송료·수수료는 포함하지 않는다(주문 단계에서 계산).
                    경기당 1회만 호출 가능하며, 재호출 시 409를 반환한다.
            """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "좌석 재고 오픈 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "미인증", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403", description = "ADMIN 권한 없음", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404", description = "GAME_NOT_FOUND", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "SEAT_INVENTORY_ALREADY_OPENED", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PostMapping("/{gameId}/seats")
    public ResponseEntity<ApiResponse<GameSeatOpenResponse>> openSeatInventory(
        @Parameter(description = "경기 ID", example = "1146")
        @PathVariable Long gameId
    ) {
        GameSeatOpenResponse response = gameSeatCreateService.openInventory(gameId);

        // 리소스가 새로 생성됐으므로 201.
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("좌석 재고 오픈 성공", response));
    }
}
