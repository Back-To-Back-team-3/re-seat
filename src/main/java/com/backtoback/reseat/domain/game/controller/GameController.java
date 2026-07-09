package com.backtoback.reseat.domain.game.controller;

import com.backtoback.reseat.domain.game.dto.GameDetailResponse;
import com.backtoback.reseat.domain.game.dto.GameListResponse;
import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.service.GameQueryService;
import com.backtoback.reseat.domain.game.service.GameSearchCondition;
import com.backtoback.reseat.global.common.ApiResponse;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경기 조회 API Controller.
 *
 * <p>경기 목록과 경기 상세 조회를 제공한다.
 * 경기 조회는 예매 흐름의 진입점이므로 인증 없이 접근 가능한 공개 API이다.</p>
 */
@Tag(name = "Game", description = "경기 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/games")
public class GameController {

    private final GameQueryService gameQueryService;

    /**
     * 경기 목록 조회.
     *
     * <p>팀, 날짜 범위, 예매 상태로 필터링할 수 있다.
     * 기본 페이징 크기는 20건이며 기본 정렬은 경기 일시 오름차순이다.</p>
     *
     * @param homeTeamId 홈팀 ID
     * @param awayTeamId 원정팀 ID
     * @param from 검색 시작 날짜
     * @param to 검색 종료 날짜
     * @param bookingStatus 예매 상태
     * @param pageable 페이징 조건
     * @return 경기 목록 응답
     */
    @Operation(
        summary = "경기 목록 조회",
        description = """
            예매 대상 경기 목록을 조회한다.

            - 인증 없이 접근 가능한 공개 API
            - 홈팀, 원정팀, 날짜 범위, 예매 상태로 필터링 가능
            - 기본 페이지 크기: 20
            - 기본 정렬: gameAt ASC
            - Spring Pageable 기본 규칙에 따라 page는 0부터 시작
            - homeTeam, awayTeam, stadium은 fetch join으로 함께 조회되어 N+1이 발생하지 않음
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "경기 목록 조회 성공",
            content = @Content(
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(
                    name = "경기 목록 조회 성공 예시",
                    value = """
                        {
                            "success": true,
                            "data": {
                                "content": [
                                    {
                                        "gameId": 1,
                                        "title": "LG vs 한화",
                                        "homeTeam": {
                                            "teamId": 1,
                                            "name": "LG"
                                        },
                                        "awayTeam": {
                                            "teamId": 2,
                                            "name": "한화"
                                        },
                                        "stadium": {
                                            "stadiumId": 1,
                                            "name": "잠실야구장"
                                        },
                                        "gameAt": "2026-07-11 18:30:00",
                                        "bookingOpenAt": "2026-07-04 14:00:00",
                                        "bookingCloseAt": "2026-07-11 18:30:00",
                                        "bookingStatus": "OPEN"
                                    }
                                ],
                                "pageable": {
                                    "pageNumber": 0,
                                    "pageSize": 20
                                },
                                "totalElements": 1,
                                "totalPages": 1,
                                "first": true,
                                "last": true
                            },
                            "message": "경기 목록 조회 성공"
                        }
                        """
                )
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "요청 파라미터 오류",
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
        )
    })
    @GetMapping
    public ApiResponse<Page<GameListResponse>> getGames(
        @Parameter(description = "홈팀 ID", example = "1")
        @RequestParam(required = false) Long homeTeamId,

        @Parameter(description = "원정팀 ID", example = "2")
        @RequestParam(required = false) Long awayTeamId,

        @Parameter(description = "검색 시작 날짜", example = "2026-07-01")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,

        @Parameter(description = "검색 종료 날짜", example = "2026-07-31")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,

        @Parameter(description = "예매 상태", example = "OPEN")
        @RequestParam(required = false) BookingStatus bookingStatus,

        @Parameter(description = "페이징 조건. page는 0부터 시작한다.")
        @PageableDefault(size = 20, sort = "gameAt", direction = Sort.Direction.ASC)
        Pageable pageable
    ) {
        GameSearchCondition condition = new GameSearchCondition(
            homeTeamId,
            awayTeamId,
            from,
            to,
            bookingStatus
        );

        Page<GameListResponse> response = gameQueryService.getGames(condition, pageable);

        return ApiResponse.success("경기 목록 조회 성공", response);
    }

    /**
     * 경기 상세 조회.
     *
     * <p>사용자가 특정 경기를 선택했을 때 예매 진입 판단에 필요한
     * 경기 기준 정보를 반환한다. 좌석 재고와 가격 정보는 포함하지 않는다.</p>
     *
     * @param gameId 경기 ID
     * @return 경기 상세 응답
     */
    @GetMapping("/{gameId}")
    public ApiResponse<GameDetailResponse> getGame(
        @Parameter(description = "경기 ID", example = "1", required = true)
        @PathVariable Long gameId
    ) {
        GameDetailResponse response = gameQueryService.getGame(gameId);

        return ApiResponse.success("경기 상세 조회 성공", response);
    }
}
