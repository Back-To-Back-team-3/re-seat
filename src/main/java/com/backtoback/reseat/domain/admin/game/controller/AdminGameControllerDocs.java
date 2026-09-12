package com.backtoback.reseat.domain.admin.game.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.game.dto.GameListResponse;
import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.global.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 관리자 전용 경기 목록 조회·검색 API Swagger 문서화 인터페이스.
 * <p>ROLE_ADMIN 전용 API이므로 JWT Bearer Token @SecurityRequirement만 선언한다.
 * ADMIN 권한 검증은 Spring Security가 처리한다.
 */
@Tag(
    name = "Admin - Game Search",
    description = "관리자 경기 목록 조회·검색 API (ROLE_ADMIN 전용)"
)
public interface AdminGameControllerDocs {

    @Operation(
        summary = "관리자 경기 목록 조회",
        description = """
            ROLE_ADMIN 필요. 경기일 범위·구장·구단·예매 상태 조건으로 경기 목록을 조회한다.

            - 공개 조회(GET /api/v1/games)와 동일한 응답 스키마를 사용한다.
            - stadiumId 조건은 관리자 조회 전용으로 추가됐다(공개 API에는 없음).
            - 상태 전이(PATCH)는 포함하지 않으며, 전이는 별도 API(PATCH /{gameId}/booking-status)를 사용한다.
            - 기본 페이지 크기: 20
            - 기본 정렬: gameAt ASC
            - 허용 sort 필드: gameAt, bookingOpenAt, bookingCloseAt, id
            """,
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "관리자 경기 목록 조회 성공",
                content = @Content(
                    examples = @ExampleObject(
                        name = "관리자 경기 목록 조회 성공 예시",
                        value = """
                            {
                                "success": true,
                                "message": "관리자 경기 목록 조회 성공",
                                "data": {
                                    "content": [
                                        {
                                            "gameId": 1,
                                            "title": "LG vs 한화",
                                            "homeTeam": { "teamId": 1, "name": "LG" },
                                            "awayTeam": { "teamId": 2, "name": "한화" },
                                            "stadium": { "stadiumId": 1, "name": "잠실야구장" },
                                            "gameAt": "2026-07-11 18:30:00",
                                            "bookingOpenAt": "2026-07-04 14:00:00",
                                            "bookingCloseAt": "2026-07-11 18:30:00",
                                            "bookingStatus": "OPEN"
                                        }
                                    ],
                                    "pageNumber": 0,
                                    "pageSize": 20,
                                    "totalElements": 1,
                                    "totalPages": 1,
                                    "isFirst": true,
                                    "isLast": true
                                }
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST — 파라미터 타입 오류 또는 validation 실패",
                content = @Content(
                    examples = @ExampleObject(
                        name = "잘못된 요청 예시",
                        value = """
                            {
                                "success": false,
                                "errorCode": "INVALID_REQUEST",
                                "message": "요청 값이 올바르지 않습니다."
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
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<PageResponse<GameListResponse>>> searchGames(
        @Parameter(
            description = "홈팀 ID. 미존재 ID는 에러가 아니라 빈 결과 반환",
            example = "1"
        ) Long homeTeamId,

        @Parameter(
            description = "원정팀 ID. 미존재 ID는 에러가 아니라 빈 결과 반환",
            example = "2"
        ) Long awayTeamId,

        @Parameter(
            description = "구장 ID. 관리자 조회 전용 조건이며, 미존재 ID는 에러가 아니라 빈 결과 반환",
            example = "1"
        ) Long stadiumId,

        @Parameter(
            description = "경기일 검색 시작일 (해당일 00:00:00 포함, yyyy-MM-dd)",
            example = "2026-07-01"
        ) LocalDate from,

        @Parameter(
            description = "경기일 검색 종료일 (해당일 23:59:59까지 포함, yyyy-MM-dd)",
            example = "2026-07-31"
        ) LocalDate to,

        @Parameter(
            description = "예매 상태 필터",
            example = "OPEN",
            schema = @Schema(
                allowableValues = {
                    "SCHEDULED",
                    "OPEN",
                    "CLOSED",
                    "CANCELLED"
                }
            )
        ) BookingStatus bookingStatus,

        @Parameter(description = "페이징 조건. page는 0부터 시작, 기본 sort: gameAt,asc") Pageable pageable
    );
}
