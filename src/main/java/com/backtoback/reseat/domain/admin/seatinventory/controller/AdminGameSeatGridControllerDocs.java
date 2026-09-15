package com.backtoback.reseat.domain.admin.seatinventory.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.seatinventory.dto.SeatStatusResponse;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 관리자용 좌석 그리드 조회 API Swagger 문서화 인터페이스.
 * <p>ROLE_ADMIN 전용 API이므로 JWT Bearer Token @SecurityRequirement만 선언한다.
 * ADMIN 권한 검증은 Spring Security가 처리한다. Queue-Token은 요구하지 않는다.
 */
@Tag(
    name = "Admin - Game Seat",
    description = "경기 좌석 재고 관리 API (ROLE_ADMIN 전용)"
)
public interface AdminGameSeatGridControllerDocs {

    @Operation(
        summary = "경기 좌석 그리드 조회",
        description = """
            ROLE_ADMIN 필요. 경기의 개별 좌석 상태를 zoneId·status 필터로 조회한다.

            공개 API(4.1)와 조회 로직은 동일하나 Queue-Token을 요구하지 않는다.
            관리자가 판매 차단 대상을 고를 때 사용한다.
            """,
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "좌석 그리드 조회 성공",
                content = @Content(
                    examples = @ExampleObject(
                        name = "좌석 그리드 조회 성공 예시",
                        value = """
                            {
                                "success": true,
                                "errorCode": null,
                                "message": "좌석 그리드 조회",
                                "data": [
                                    {
                                        "gameSeatId": 5001,
                                        "zoneId": 30,
                                        "zoneName": "1루 블루석",
                                        "grade": "INFIELD",
                                        "seatBlock": "A",
                                        "seatRow": "3",
                                        "seatNumber": "12",
                                        "price": 18000,
                                        "status": "AVAILABLE"
                                    }
                                ]
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
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<List<SeatStatusResponse>>> getSeatGrid(
        @Parameter(
            description = "경기 ID",
            example = "10",
            required = true
        ) Long gameId,
        @Parameter(
            description = "구역 필터",
            example = "30"
        ) Long zoneId,
        @Parameter(
            description = "상태 필터",
            example = "AVAILABLE"
        ) GameSeatStatus status
    );
}
