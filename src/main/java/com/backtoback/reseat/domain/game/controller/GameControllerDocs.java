package com.backtoback.reseat.domain.game.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.game.dto.GameDetailResponse;
import com.backtoback.reseat.domain.game.dto.GameListResponse;
import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.global.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 경기 조회 API Swagger 문서화 인터페이스.
 *
 * <p>두 엔드포인트 모두 공개 API이므로 인증 불필요</p>
 */
@Tag(name = "Game", description = "경기 조회 API")
public interface GameControllerDocs {

    @Operation(summary = "경기 목록 조회", description = """
        예매 대상 경기 목록을 조회한다.

        - 인증 없이 접근 가능한 공개 API
        - 홈팀, 원정팀, 날짜 범위, 예매 상태로 필터링 가능
        - 기본 페이지 크기: 20
        - 기본 정렬: gameAt ASC
        - 허용 sort 필드: gameAt, bookingOpenAt, bookingCloseAt, id
        - 그 외 sort 필드는 무시되고 id ASC 보조정렬이 뒤에 붙음
        """, security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "경기 목록 조회 성공", content = @Content(examples = @ExampleObject(name = "경기 목록 조회 성공 예시", value = """
            {
                "success": true,
                "message": "경기 목록 조회 성공",
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
            """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_REQUEST — 파라미터 타입 오류 또는 validation 실패", content = @Content(examples = @ExampleObject(name = "잘못된 요청 예시", value = """
            {
                "success": false,
                "errorCode": "INVALID_REQUEST",
                "message": "요청 값이 올바르지 않습니다."
            }
            """)))
    })
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<PageResponse<GameListResponse>>> getGames(
        @Parameter(description = "홈팀 ID. 미존재 ID는 에러가 아니라 빈 결과 반환", example = "1")
        Long homeTeamId,

        @Parameter(description = "원정팀 ID. 미존재 ID는 에러가 아니라 빈 결과 반환", example = "2")
        Long awayTeamId,

        @Parameter(description = "경기일 검색 시작일 (해당일 00:00:00 포함, yyyy-MM-dd)", example = "2026-07-01")
        LocalDate from,

        @Parameter(description = "경기일 검색 종료일 (해당일 23:59:59까지 포함, yyyy-MM-dd)", example = "2026-07-31")
        LocalDate to,

        @Parameter(description = "예매 상태 필터", example = "OPEN", schema = @Schema(allowableValues = {"SCHEDULED", "OPEN",
            "CLOSED", "CANCELLED"}))
        BookingStatus bookingStatus,

        @Parameter(description = "페이징 조건. page는 0부터 시작, 기본 sort: gameAt,asc")
        Pageable pageable);

    @Operation(summary = "경기 상세 조회", description = """
        특정 경기의 상세 정보를 조회한다.

        - 인증 없이 접근 가능한 공개 API
        - 목록 응답과 달리 stadium에 address, totalCapacity를 추가로 포함
        - 좌석 재고와 가격 정보는 포함하지 않음
        """, security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "경기 상세 조회 성공", content = @Content(examples = @ExampleObject(name = "경기 상세 조회 성공 예시", value = """
            {
                "success": true,
                "message": "경기 상세 조회 성공",
                "data": {
                    "gameId": 1,
                    "title": "LG vs 한화",
                    "homeTeam": { "teamId": 1, "name": "LG" },
                    "awayTeam": { "teamId": 2, "name": "한화" },
                    "stadium": {
                        "stadiumId": 1,
                        "name": "잠실야구장",
                        "address": "서울특별시 송파구 올림픽로 25",
                        "totalCapacity": 25000
                    },
                    "gameAt": "2026-07-11 18:30:00",
                    "bookingOpenAt": "2026-07-04 14:00:00",
                    "bookingCloseAt": "2026-07-11 18:30:00",
                    "bookingStatus": "OPEN"
                }
            }
            """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "INVALID_REQUEST — gameId 타입 오류 (숫자 아님)", content = @Content(examples = @ExampleObject(name = "타입 오류 예시", value = """
            {
                "success": false,
                "errorCode": "INVALID_REQUEST",
                "message": "요청 값이 올바르지 않습니다."
            }
            """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GAME_NOT_FOUND — 미존재 gameId", content = @Content(examples = @ExampleObject(name = "경기 없음 예시", value = """
            {
                "success": false,
                "errorCode": "GAME_NOT_FOUND",
                "message": "경기를 찾을 수 없습니다."
            }
            """)))
    })
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<GameDetailResponse>> getGame(
        @Parameter(description = "경기 ID", example = "1", required = true)
        Long gameId);
}
