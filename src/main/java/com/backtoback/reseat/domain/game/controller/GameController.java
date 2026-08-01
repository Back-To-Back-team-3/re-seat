package com.backtoback.reseat.domain.game.controller;

import com.backtoback.reseat.domain.game.dto.GameDetailResponse;
import com.backtoback.reseat.domain.game.dto.GameListResponse;
import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.service.GameQueryService;
import com.backtoback.reseat.domain.game.service.GameSearchCondition;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.common.PageResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
public class GameController implements GameControllerDocs {

    private final GameQueryService gameQueryService;

    /**
     * 경기 목록 조회.
     *
     * @param homeTeamId 홈팀 ID
     * @param awayTeamId 원정팀 ID
     * @param from 검색 시작 날짜
     * @param to 검색 종료 날짜
     * @param bookingStatus 예매 상태
     * @param pageable 페이징 조건
     * @return 경기 목록 응답
     */
    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<GameListResponse>>> getGames(
        @RequestParam(required = false) Long homeTeamId,
        @RequestParam(required = false) Long awayTeamId,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) BookingStatus bookingStatus,
        @PageableDefault(size = 20, sort = "gameAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        GameSearchCondition condition = new GameSearchCondition(
            homeTeamId,
            awayTeamId,
            from,
            to,
            bookingStatus
        );
        Page<GameListResponse> response = gameQueryService.getGames(condition, pageable);
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("경기 목록 조회 성공", PageResponse.of(response)));
    }

    /**
     * 경기 상세 조회.
     *
     * @param gameId 경기 ID
     * @return 경기 상세 응답
     */
    @Override
    @GetMapping("/{gameId}")
    public ResponseEntity<ApiResponse<GameDetailResponse>> getGame(
        @PathVariable Long gameId
    ) {
        GameDetailResponse response = gameQueryService.getGame(gameId);
        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("경기 상세 조회 성공", response));
    }
}
