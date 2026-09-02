package com.backtoback.reseat.domain.admin.game.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.admin.game.dto.request.GameBookingStatusUpdateRequest;
import com.backtoback.reseat.domain.admin.game.dto.response.GameBookingStatusResponse;
import com.backtoback.reseat.domain.admin.game.service.AdminGameBookingService;
import com.backtoback.reseat.global.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 전용 경기 예매 상태 전이 API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/games")
public class AdminGameBookingController {

    private final AdminGameBookingService adminGameBookingService;

    /**
     * 경기 예매 상태를 전이한다(OPEN/CLOSED/CANCELLED).
     */
    @PatchMapping("/{gameId}/booking-status")
    public ResponseEntity<ApiResponse<GameBookingStatusResponse>> updateBookingStatus(
        @PathVariable Long gameId,
        @Valid @RequestBody GameBookingStatusUpdateRequest request
    ) {

        var target = request.toBookingStatus();
        adminGameBookingService.transition(gameId, target, request.getReason());

        return ResponseEntity.ok(ApiResponse.success("예매 상태 변경", GameBookingStatusResponse.from(gameId, target)));
    }
}
