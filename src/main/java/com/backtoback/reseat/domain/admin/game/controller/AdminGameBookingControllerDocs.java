package com.backtoback.reseat.domain.admin.game.controller;

import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.admin.game.dto.request.GameBookingStatusUpdateRequest;
import com.backtoback.reseat.domain.admin.game.dto.response.GameBookingStatusResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 관리자용 경기 예매 상태 전이 API Swagger 문서화 인터페이스.
 * <p>ROLE_ADMIN 전용 API이므로 JWT Bearer Token @SecurityRequirement만 선언한다.
 * ADMIN 권한 검증은 Spring Security가 처리한다.
 */
@Tag(
    name = "Admin - Game Booking",
    description = "경기 예매 오픈/마감/취소 상태 전이 API (ROLE_ADMIN 전용)"
)
public interface AdminGameBookingControllerDocs {

    @Operation(
        summary = "경기 예매 상태 전이",
        description = """
            ROLE_ADMIN 필요. 경기의 예매 상태를 오픈·마감·취소로 전이한다.

            허용 전이: SCHEDULED→OPEN, OPEN→CLOSED, SCHEDULED/OPEN/CLOSED→CANCELLED
            OPEN 전이는 해당 경기의 좌석 재고(game_seats)가 이미 생성돼 있어야 한다.
            동일 경기의 동시 전이 요청 중 하나만 성공한다(조건부 원자적 갱신).
            """,
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "예매 상태 전이 성공",
                content = @Content(
                    examples = @ExampleObject(
                        name = "상태 전이 성공 예시",
                        value = """
                            {
                                "success": true,
                                "message": "예매 상태 변경",
                                "data": {
                                    "gameId": 10,
                                    "bookingStatus": "OPEN"
                                }
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST — bookingStatus/reason 형식 오류 또는 누락",
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
                description = "GAME_NOT_FOUND — 미존재 gameId",
                content = @Content(
                    examples = @ExampleObject(
                        name = "경기 없음 예시",
                        value = """
                            {
                                "success": false,
                                "errorCode": "GAME_NOT_FOUND",
                                "message": "경기를 찾을 수 없습니다. gameId=999"
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "INVALID_BOOKING_STATUS_TRANSITION(허용되지 않은 전이·동시 요청 경합) "
                    + "/ SEAT_INVENTORY_NOT_OPENED(좌석 재고 미오픈 상태에서 OPEN 시도)",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<GameBookingStatusResponse>> updateBookingStatus(
        @Parameter(
            description = "경기 ID",
            example = "10",
            required = true
        ) Long gameId,
        GameBookingStatusUpdateRequest request
    );
}
