package com.backtoback.reseat.domain.reservation.controller;

import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.dto.response.HoldTimeResponse;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationCancelResponse;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

/**
 * 좌석 선점(HOLD)·남은시간 조회·해제 API Swagger 문서화 인터페이스.
 * <p>좌석 선점은 JWT + Queue-Token 이중 인증이 필요하다.
 * 남은시간 조회, 선점 해제는 JWT 단독 인증이다.
 * SwaggerConfig에 등록된 스킴명을 그대로 사용한다.
 * - "JWT Bearer Token" (HTTP Bearer)
 * - "Queue-Token" (API Key Header)
 */
@Tag(
    name = "Reservation",
    description = "좌석 선점·남은시간 조회·해제 API"
)
public interface ReservationControllerDocs {

    @Operation(
        summary = "좌석 선점 (HOLD)",
        description = """
            대기열을 통과한 사용자가 최대 2석을 임시 선점합니다. 선점 유효 시간은 10분입니다.

            사전 검증 순서:
            1. 예매 가능 여부: games.booking_status = OPEN → BOOKING_NOT_OPEN(409)
            2. 본인인증: users.is_verified = false → USER_NOT_VERIFIED(403)
            3. Queue-Token: 누락/무효/만료/사용됨 → QUEUE_TOKEN_*(403/409/410) — 검증만 하며 소비하지 않습니다.
            4. 수량 제한: HELD+SOLD 좌석 수 + 요청 수 > 2 → MAX_SEAT_COUNT_EXCEEDED(400)
            5. 좌석 상태·소속 검증 → 락 획득 → HELD 전이

            락 전략: Redisson 분산락 또는 DB 비관적 락(FOR UPDATE).
            gameSeatId 오름차순 정렬 후 락 획득(데드락 방지).
            처리 흐름: Queue-Token 검증 → 분산 락 → 선점 트랜잭션.
            """,
        security = {
            @SecurityRequirement(name = "JWT Bearer Token"),
            @SecurityRequirement(name = "Queue-Token")
        }
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201",
                description = "좌석 선점 성공",
                content = @Content(
                    examples = @ExampleObject(
                        name = "선점 성공 예시",
                        value = """
                            {
                                "success": true,
                                "message": "좌석 선점 성공",
                                "data": {
                                    "reservationId": 1001,
                                    "reservationNo": "RSV-20260711-A7B3C1",
                                    "status": "HOLDING",
                                    "gameSeats": [
                                        {
                                            "gameSeatId": 5001,
                                            "status": "HELD",
                                            "price": 18000
                                        },
                                        {
                                            "gameSeatId": 5002,
                                            "status": "HELD",
                                            "price": 18000
                                        }
                                    ],
                                    "holdExpiresAt": "2026-07-11T18:37:00",
                                    "gameAt": "2026-07-11T18:30:00"
                                }
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST / MAX_SEAT_COUNT_EXCEEDED / GAME_SEAT_NOT_IN_GAME",
                content = @Content(
                    examples = @ExampleObject(
                        name = "수량 초과 예시",
                        value = """
                            {
                                "success": false,
                                "errorCode": "MAX_SEAT_COUNT_EXCEEDED",
                                "message": "1인당 최대 예매 좌석 수를 초과했습니다."
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "UNAUTHORIZED — JWT 누락 또는 만료",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "USER_NOT_VERIFIED / QUEUE_TOKEN_REQUIRED / QUEUE_TOKEN_INVALID",
                content = @Content(
                    examples = @ExampleObject(
                        name = "본인인증 미완료 예시",
                        value = """
                            {
                                "success": false,
                                "errorCode": "USER_NOT_VERIFIED",
                                "message": "본인인증 처리에 실패했습니다."
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "GAME_NOT_FOUND / GAME_SEAT_NOT_FOUND",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "BOOKING_NOT_OPEN / SEAT_ALREADY_HELD / SEAT_ALREADY_SOLD / SEAT_BLOCKED / LOCK_FAILED / QUEUE_TOKEN_ALREADY_USED",
                content = @Content(
                    examples = @ExampleObject(
                        name = "이미 선점된 좌석 예시",
                        value = """
                            {
                                "success": false,
                                "errorCode": "SEAT_ALREADY_HELD",
                                "message": "이미 선점된 좌석입니다."
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "410",
                description = "QUEUE_TOKEN_EXPIRED — 만료된 Queue-Token",
                content = @Content(
                    examples = @ExampleObject(
                        name = "토큰 만료 예시",
                        value = """
                            {
                                "success": false,
                                "errorCode": "QUEUE_TOKEN_EXPIRED",
                                "message": "만료된 입장 토큰입니다."
                            }
                            """
                    )
                )
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<ReservationResponse>> holdSeats(
        @Parameter(
            description = "대기열 입장 토큰",
            example = "qt_c6f443cf-a0d7-467f-b93f-da417c135a97"
        ) String queueToken,

        @Parameter(hidden = true) CustomUserDetails userDetails,

        SeatHoldRequest request
    );

    @Operation(
        summary = "선점 남은 시간 조회",
        description = """
            선점 만료까지 남은 시간(초)을 반환합니다.
            이미 만료된 경우 remainingSeconds = 0.
            expiresAt은 클라이언트 카운트다운 동기화용 만료 절대 시각입니다.
            """,
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "선점 잔여 시간 조회 성공",
                content = @Content(
                    examples = @ExampleObject(
                        name = "잔여 시간 조회 성공 예시",
                        value = """
                            {
                                "success": true,
                                "message": "선점 잔여 시간 조회 성공",
                                "data": {
                                    "reservationId": 1001,
                                    "remainingSeconds": 230,
                                    "status": "HOLDING",
                                    "expiresAt": "2026-07-11T18:37:00"
                                }
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "UNAUTHORIZED — JWT 누락 또는 만료",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "RESERVATION_ACCESS_DENIED — 타인 예약",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "RESERVATION_NOT_FOUND",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<HoldTimeResponse>> getHoldTime(
        @Parameter(
            description = "예약 ID",
            example = "1001",
            required = true
        ) Long reservationId,

        @Parameter(hidden = true) CustomUserDetails userDetails
    );

    @Operation(
        summary = "선점 해제",
        description = """
            선점된 좌석을 해제합니다.
            좌석은 즉시 AVAILABLE 상태로 복귀합니다.
            이미 만료된 선점 해제 시 410 PRE_RESERVATION_EXPIRED.
            이미 취소된 예약에 대한 재취소 요청은 200으로 멱등 처리됩니다.
            """,
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "선점 해제 성공",
                content = @Content(
                    examples = @ExampleObject(
                        name = "선점 해제 성공 예시",
                        value = """
                            {
                                "success": true,
                                "message": "좌석 선점 해제 성공",
                                "data": {
                                    "reservationId": 1001,
                                    "status": "CANCELED"
                                }
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "UNAUTHORIZED — JWT 누락 또는 만료",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "RESERVATION_ACCESS_DENIED — 타인 예약",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "RESERVATION_NOT_FOUND",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "410",
                description = "PRE_RESERVATION_EXPIRED — 이미 만료된 선점",
                content = @Content(
                    examples = @ExampleObject(
                        name = "선점 만료 예시",
                        value = """
                            {
                                "success": false,
                                "errorCode": "PRE_RESERVATION_EXPIRED",
                                "message": "좌석 선점 시간이 만료되었습니다."
                            }
                            """
                    )
                )
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<ReservationCancelResponse>> releaseHold(
        @Parameter(
            description = "예약 ID",
            example = "1001",
            required = true
        ) Long reservationId,

        @Parameter(hidden = true) CustomUserDetails userDetails
    );
}
