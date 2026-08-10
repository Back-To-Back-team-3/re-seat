package com.backtoback.reseat.domain.seatinventory.controller;

import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.seatinventory.dto.GameSeatOpenResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 관리자용 경기 좌석 재고 API Swagger 문서화 인터페이스.
 *
 * <p>ROLE_ADMIN 전용 API이므로 JWT Bearer Token @SecurityRequirement만 선언한다.
 * ADMIN 권한 검증은 Spring Security가 처리한다.
 */
@Tag(name = "Admin - Game Seat", description = "경기 좌석 재고 관리 API (ROLE_ADMIN 전용)")
public interface AdminGameSeatControllerDocs {

    @Operation(summary = "경기 좌석 재고 오픈", description = """
        ROLE_ADMIN 필요. 해당 경기의 판매 좌석 재고(game_seats)를 일괄 생성한다.

        가격 산정식: 구역 성인 기본가(요일 반영) × 시기 배수
          - 화~목: seat_zones.base_price (INFIELD 18,000 / OUTFIELD 16,000)
          - 금~월: INFIELD 25,000 / OUTFIELD 23,000
          - 9~10월: × 1.2

        가격은 서버 정책이 결정한다. 요청 바디 없음.
        경기당 1회만 호출 가능하며 재호출 시 409를 반환한다.
        """, security = @SecurityRequirement(name = "JWT Bearer Token"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "좌석 재고 오픈 성공", content = @Content(examples = @ExampleObject(name = "재고 오픈 성공 예시", value = """
            {
                "success": true,
                "message": "좌석 재고 오픈 성공",
                "data": {
                    "gameId": 1146,
                    "createdCount": 500,
                    "priceRange": {
                        "min": 16000,
                        "max": 18000
                    }
                }
            }
            """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "UNAUTHORIZED — 미인증", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "FORBIDDEN — ADMIN 권한 없음", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GAME_NOT_FOUND — 미존재 gameId", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "SEAT_INVENTORY_ALREADY_OPENED — 재호출", content = @Content)
    })
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<GameSeatOpenResponse>> openSeatInventory(
        @Parameter(description = "경기 ID", example = "1146", required = true)
        Long gameId);
}
