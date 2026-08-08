package com.backtoback.reseat.domain.seatinventory.controller;

import com.backtoback.reseat.domain.seatinventory.dto.SeatStatusResponse;
import com.backtoback.reseat.domain.seatinventory.dto.ZoneSummaryResponse;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * 경기 좌석 현황·구역 조회 API Swagger 문서화 인터페이스.
 *
 * <p>좌석 조회는 JWT + Queue-Token 이중 인증이 필요하다. 구역 조회는 JWT 단독 인증이다.
 * SwaggerConfig에 등록된 스킴명을 그대로 사용한다.
 * - "JWT Bearer Token" (HTTP Bearer)
 * - "Queue-Token"      (API Key Header)
 */
@Tag(name = "Game Seat", description = "경기 좌석 현황·구역 조회 API")
public interface GameSeatControllerDocs {

    @Operation(
        summary = "경기 좌석 현황 조회",
        description = """
            경기의 좌석 현황을 조회한다.

            - JWT 인증 + Queue-Token 검증 필수
            - 필터 미지정 시 전체 500건 반환
            - zoneId 필터로 구역 단위 부분 조회 가능
            - Queue-Token 검증: validateToken(조회)만 수행.
              토큰 소비(consumeToken)는 holdSeats 성공 후 호출한다.
            - 재고 미오픈 경기 조회 시 409 SEAT_INVENTORY_NOT_OPENED
            """,
        security = {
            @SecurityRequirement(name = "JWT Bearer Token"),
            @SecurityRequirement(name = "Queue-Token")
        }
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "좌석 현황 조회 성공",
            content = @Content(
                examples = @ExampleObject(
                    name = "좌석 현황 조회 성공 예시",
                    value = """
                        {
                            "success": true,
                            "message": "좌석 현황 조회 성공",
                            "data": [
                                {
                                    "gameSeatId": 1001,
                                    "zoneId": 1,
                                    "zoneName": "1루 101",
                                    "grade": "INFIELD",
                                    "seatBlock": "101",
                                    "seatRow": "A",
                                    "seatNumber": "1",
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
            description = "UNAUTHORIZED — JWT 누락 또는 만료",
            content = @Content(
                examples = @ExampleObject(
                    name = "인증 실패 예시",
                    value = """
                        {
                            "success": false,
                            "errorCode": "UNAUTHORIZED",
                            "message": "인증 정보가 유효하지 않거나 만료되었습니다."
                        }
                        """
                )
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "QUEUE_TOKEN_REQUIRED / QUEUE_TOKEN_INVALID",
            content = @Content(
                examples = @ExampleObject(
                    name = "Queue-Token 오류 예시",
                    value = """
                        {
                            "success": false,
                            "errorCode": "QUEUE_TOKEN_REQUIRED",
                            "message": "입장 토큰이 필요합니다."
                        }
                        """
                )
            )
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
                            "message": "경기를 찾을 수 없습니다."
                        }
                        """
                )
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "SEAT_INVENTORY_NOT_OPENED — 재고 미오픈",
            content = @Content(
                examples = @ExampleObject(
                    name = "재고 미오픈 예시",
                    value = """
                        {
                            "success": false,
                            "errorCode": "SEAT_INVENTORY_NOT_OPENED",
                            "message": "좌석 재고가 아직 오픈되지 않았습니다."
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
    })
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<List<SeatStatusResponse>>> getSeats(
        @Parameter(description = "경기 ID", example = "1146", required = true)
        Long gameId,

        @Parameter(description = "구역 ID (선택)")
        Long zoneId,

        @Parameter(description = "좌석 등급 (선택)",
            schema = @io.swagger.v3.oas.annotations.media.Schema(
                allowableValues = {"INFIELD", "OUTFIELD"}))
        SeatGrade grade,

        @Parameter(description = "좌석 상태 (선택)",
            schema = @io.swagger.v3.oas.annotations.media.Schema(
                allowableValues = {"AVAILABLE", "HELD", "SOLD", "BLOCKED"}))
        GameSeatStatus status,

        @Parameter(description = "대기열 통과 토큰", example = "qt_c6f443cf-a0d7-467f-b93f-da417c135a97")
        String queueToken,

        @Parameter(hidden = true)
        CustomUserDetails userDetails
    );

    @Operation(
        summary = "경기 구역별 잔여 좌석 조회",
        description = """
            경기의 구역별 잔여 좌석 수를 집계해 반환한다.

            - JWT 인증 필수
            - 잔여수가 0인 구역(매진)도 결과에 포함됨
            - 재고 미오픈 경기 조회 시 409 SEAT_INVENTORY_NOT_OPENED
            """,
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "구역 요약 조회 성공",
            content = @Content(
                examples = @ExampleObject(
                    name = "구역 요약 조회 성공 예시",
                    value = """
                        {
                            "success": true,
                            "message": "구역 요약 조회 성공",
                            "data": [
                                {
                                    "zoneId": 1,
                                    "zoneName": "1루 101",
                                    "grade": "INFIELD",
                                    "basePrice": 18000,
                                    "totalCount": 50,
                                    "availableCount": 42
                                }
                            ]
                        }
                        """
                )
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "UNAUTHORIZED — JWT 누락 또는 만료",
            content = @Content(
                examples = @ExampleObject(
                    name = "인증 실패 예시",
                    value = """
                        {
                            "success": false,
                            "errorCode": "UNAUTHORIZED",
                            "message": "인증 정보가 유효하지 않거나 만료되었습니다."
                        }
                        """
                )
            )
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
                            "message": "경기를 찾을 수 없습니다."
                        }
                        """
                )
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "SEAT_INVENTORY_NOT_OPENED — 재고 미오픈",
            content = @Content(
                examples = @ExampleObject(
                    name = "재고 미오픈 예시",
                    value = """
                        {
                            "success": false,
                            "errorCode": "SEAT_INVENTORY_NOT_OPENED",
                            "message": "좌석 재고가 아직 오픈되지 않았습니다."
                        }
                        """
                )
            )
        )
    })
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<List<ZoneSummaryResponse>>> getZoneSummaries(
        @Parameter(description = "경기 ID", example = "1146", required = true)
        Long gameId
    );
}
