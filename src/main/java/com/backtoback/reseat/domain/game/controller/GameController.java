package com.backtoback.reseat.domain.game.controller;

import com.backtoback.reseat.domain.game.dto.GameDetailResponse;
import com.backtoback.reseat.domain.game.dto.GameListResponse;
import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.service.GameQueryService;
import com.backtoback.reseat.domain.game.service.GameSearchCondition;
import com.backtoback.reseat.global.common.ApiResponse;
import java.time.LocalDate;
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
     * @param teamId 홈팀 또는 원정팀 ID
     * @param from 검색 시작 날짜
     * @param to 검색 종료 날짜
     * @param bookingStatus 예매 상태
     * @param pageable 페이징 조건
     * @return 경기 목록 응답
     */
    @GetMapping
    public ApiResponse<Page<GameListResponse>> getGames(
        @RequestParam(required = false) Long teamId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,
        @RequestParam(required = false) BookingStatus bookingStatus,
        @PageableDefault(size = 20, sort = "gameAt", direction = Sort.Direction.ASC)
        Pageable pageable
    ) {
        GameSearchCondition condition = new GameSearchCondition(
            teamId,
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
        @PathVariable Long gameId
    ) {
        GameDetailResponse response = gameQueryService.getGame(gameId);

        return ApiResponse.success("경기 상세 조회 성공", response);
    }
}
