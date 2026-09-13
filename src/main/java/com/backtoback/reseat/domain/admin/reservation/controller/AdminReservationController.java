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

// 관리자 예약·선점 목록 조회 (조회 전용)
@RestController
@RequestMapping("/api/v1/admin/games/{gameId}/reservations")
@RequiredArgsConstructor
public class AdminReservationController implements AdminReservationControllerDocs {

    private final AdminReservationQueryService adminReservationQueryService;

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
