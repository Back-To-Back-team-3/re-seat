package com.backtoback.reseat.domain.admin.game.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.game.dto.GameListResponse;
import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.service.GameQueryService;
import com.backtoback.reseat.domain.game.service.GameSearchCondition;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.common.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 전용 경기 목록 조회·검색 API.
 * <p>GameQueryService·GameSearchCondition을 재사용하되, stadiumId 조건을 추가로 노출한다.
 * <p>상태 전이(PATCH)는 AdminGameBookingController가 담당하며 이 컨트롤러 범위에 포함하지 않는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/games")
public class AdminGameController implements AdminGameControllerDocs {

    private final GameQueryService gameQueryService;

    /**
     * 관리자 경기 목록 조회.
     *
     * @param homeTeamId 홈팀 ID
     * @param awayTeamId 원정팀 ID
     * @param stadiumId 구장 ID
     * @param from 검색 시작 날짜
     * @param to 검색 종료 날짜
     * @param bookingStatus 예매 상태
     * @param pageable 페이징 조건
     * @return 관리자 경기 목록 응답
     */
    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<GameListResponse>>> searchGames(
        @RequestParam(required = false) Long homeTeamId,
        @RequestParam(required = false) Long awayTeamId,
        @RequestParam(required = false) Long stadiumId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) BookingStatus bookingStatus,
        @PageableDefault(
            size = 20,
            sort = "gameAt",
            direction = Sort.Direction.ASC
        ) Pageable pageable
    ) {
        GameSearchCondition condition
            = new GameSearchCondition(homeTeamId, awayTeamId, stadiumId, from, to, bookingStatus);
        Page<GameListResponse> response = gameQueryService.getGames(condition, pageable);

        return ResponseEntity.ok(ApiResponse.success("관리자 경기 목록 조회 성공", PageResponse.of(response)));
    }
}
