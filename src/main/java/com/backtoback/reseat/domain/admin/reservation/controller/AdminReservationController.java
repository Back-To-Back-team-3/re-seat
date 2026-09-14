package com.backtoback.reseat.domain.admin.reservation.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.admin.reservation.dto.response.AdminReservationListResponse;
import com.backtoback.reseat.domain.admin.reservation.service.AdminReservationQueryService;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.common.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 전용 예약·선점 상태 관리 목록 조회 API.
 * <p>
 * 조회 전용이며 강제 해제·상태 변경 기능은 포함하지 않는다(만료 회수는 HoldExpiryScheduler가 담당).
 * 좌석 재고 자체의 차단·해제는 AdminGameSeatBlockController가 별도로 담당한다.
 */
@RestController
@RequestMapping("/api/v1/admin/games/{gameId}/reservations")
@RequiredArgsConstructor
public class AdminReservationController implements AdminReservationControllerDocs {

    private final AdminReservationQueryService adminReservationQueryService;

    /**
     * 경기별 예약·선점 상태 목록 조회.
     *
     * @param gameId 조회할 경기 ID
     * @param status 조회할 예약 상태(HOLDING/CONFIRMED/EXPIRED). 미지정 시 전체 조회
     * @param pageable 페이지 번호·크기·정렬 조건 (기본: size=20, createdAt DESC)
     * @return 200 OK + 예약·선점 상태별 목록(페이지네이션)
     */
    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminReservationListResponse>>> getReservations(
        @PathVariable Long gameId,
        @RequestParam(required = false) ReservationStatus status,
        @PageableDefault(
            size = 20,
            sort = "createdAt",
            direction = Sort.Direction.DESC
        ) Pageable pageable
    ) {
        PageResponse<AdminReservationListResponse> response
            = adminReservationQueryService.getReservations(gameId, status, pageable);

        return ResponseEntity.ok(ApiResponse.success("예약·선점 상태별 목록 조회 완료", response));
    }
}
