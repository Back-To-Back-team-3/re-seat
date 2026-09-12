package com.backtoback.reseat.domain.admin.seatinventory.controller;

import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.admin.seatinventory.dto.request.SeatBlockRequest;
import com.backtoback.reseat.domain.admin.seatinventory.dto.response.GameSeatStatusResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 관리자용 좌석 판매 차단·해제 API Swagger 문서화 인터페이스.
 * <p>ROLE_ADMIN 전용 API이므로 JWT Bearer Token @SecurityRequirement만 선언한다.
 * ADMIN 권한 검증은 Spring Security가 처리한다.
 */
@Tag(
    name = "Admin - Game Seat",
    description = "경기 좌석 재고 관리 API (ROLE_ADMIN 전용)"
)
public interface AdminGameSeatBlockControllerDocs {

    @Operation(
        summary = "좌석 판매 차단",
        description = """
            ROLE_ADMIN 필요. AVAILABLE 좌석을 BLOCKED로 전이한다.

            HELD·SOLD 좌석은 차단 대상에서 제외한다(먼저 선점 해제·환불 필요).
            사유(reason)는 admin_audit_logs 인프라가 준비되기 전까지 애플리케이션 로그로만 기록한다.
            """,
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "좌석 판매 차단 완료",
                content = @Content(
                    examples = @ExampleObject(
                        name = "차단 성공 예시",
                        value = """
                            {
                                "success": true,
                                "errorCode": null,
                                "message": "좌석 판매 차단 완료",
                                "data": {
                                    "gameSeatId": 5001,
                                    "status": "BLOCKED"
                                }
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST — reason 누락",
                content = @Content
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
                description = "GAME_SEAT_NOT_FOUND — 미존재 gameSeatId",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "INVALID_STATE_TRANSITION — AVAILABLE이 아닌 좌석 차단 시도",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<GameSeatStatusResponse>> block(
        @Parameter(
            description = "경기 좌석 재고 ID",
            example = "5001",
            required = true
        ) Long gameSeatId,
        SeatBlockRequest request
    );

    @Operation(
        summary = "좌석 차단 해제",
        description = """
            ROLE_ADMIN 필요. BLOCKED 좌석을 AVAILABLE로 되돌린다.

            사유(reason)는 admin_audit_logs 인프라가 준비되기 전까지 애플리케이션 로그로만 기록한다.
            """,
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "좌석 차단 해제 완료",
                content = @Content(
                    examples = @ExampleObject(
                        name = "해제 성공 예시",
                        value = """
                            {
                                "success": true,
                                "errorCode": null,
                                "message": "좌석 차단 해제 완료",
                                "data": {
                                    "gameSeatId": 5001,
                                    "status": "AVAILABLE"
                                }
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST — reason 누락",
                content = @Content
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
                description = "GAME_SEAT_NOT_FOUND — 미존재 gameSeatId",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "INVALID_STATE_TRANSITION — BLOCKED가 아닌 좌석 해제 시도",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<GameSeatStatusResponse>> unblock(
        @Parameter(
            description = "경기 좌석 재고 ID",
            example = "5001",
            required = true
        ) Long gameSeatId,
        SeatBlockRequest request
    );
}
