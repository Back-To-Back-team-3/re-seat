package com.backtoback.reseat.domain.admin.seatinventory.controller;

import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.seatinventory.dto.SeatInventorySummaryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 관리자용 경기 좌석 상태 요약 조회 API Swagger 문서화 인터페이스.
 * <p>ROLE_ADMIN 전용 API이므로 JWT Bearer Token @SecurityRequirement만 선언한다.
 * ADMIN 권한 검증은 Spring Security가 처리한다.
 */
@Tag(
    name = "Admin - Game Seat",
    description = "경기 좌석 재고 관리 API (ROLE_ADMIN 전용)"
)
public interface AdminGameSeatSummaryControllerDocs {

    @Operation(
        summary = "경기 좌석 상태 요약 조회",
        description = """
            ROLE_ADMIN 필요. 경기의 상태별(AVAILABLE/HELD/SOLD/BLOCKED) 좌석 수 합계를 반환한다.

            구역별 세분화는 포함하지 않는다. 구역 이름·가격은 구역 목록 조회 API를 이용한다.
            """,
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "좌석 재고 요약 조회 성공",
                content = @Content(
                    examples = @ExampleObject(
                        name = "요약 조회 성공 예시",
                        value = """
                            {
                                "success": true,
                                "errorCode": null,
                                "message": "좌석 재고 요약 조회",
                                "data": {
                                    "available": 18240,
                                    "held": 312,
                                    "sold": 6410,
                                    "blocked": 38
                                }
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "UNAUTHORIZED — 미인증",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "FORBIDDEN — ADMIN 권한 없음",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "GAME_NOT_FOUND — 미존재 gameId",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "SEAT_INVENTORY_NOT_OPENED — 좌석 재고 미오픈 경기 조회",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<SeatInventorySummaryResponse>> summary(
        @Parameter(
            description = "경기 ID",
            example = "10",
            required = true
        ) Long gameId
    );
}
