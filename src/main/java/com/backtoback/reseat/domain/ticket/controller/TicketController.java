package com.backtoback.reseat.domain.ticket.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.ticket.dto.response.TicketCancelResponse;
import com.backtoback.reseat.domain.ticket.dto.response.TicketDetailResponse;
import com.backtoback.reseat.domain.ticket.dto.response.TicketListResponse;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.service.TicketService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.common.PageResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 사용자 티켓 API 컨트롤러
 * <p>
 * API 명세 기준으로 일반 사용자 기능만 담당
 * <p>
 * 담당 기능
 * 1. 내 티켓 목록 조회 (8.1)
 * 2. 내 티켓 상세 조회 (8.2)
 * 3. 내 티켓 취소 (8.3)
 * <p>
 * 관리자 QR 검표 기능(8.4)은 별도의 AdminTicketController에서 처리
 */
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    /**
     * 내 티켓 목록 조회
     * <p>
     * GET /api/v1/tickets
     *
     * @param userDetails 현재 로그인 사용자 정보
     * @param status      티켓 상태 필터(선택)
     * @param pageable    페이지 정보
     * @return 페이지 형태의 내 티켓 목록
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TicketListResponse>>> getMyTickets(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestParam(required = false) TicketStatus status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<TicketListResponse> response = ticketService.getMyTickets(
            userDetails.getId(),
            status,
            pageable
        );

        return ResponseEntity.ok(
            ApiResponse.success("내 티켓 목록 조회 완료", PageResponse.of(response))
        );
    }

    /**
     * 내 티켓 상세 조회
     * <p>
     * GET /api/v1/tickets/{ticketId}
     *
     * @param userDetails 현재 로그인 사용자 정보
     * @param ticketId    티켓 ID
     * @return 티켓 상세 응답
     */
    @GetMapping("/{ticketId}")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> getTicket(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long ticketId
    ) {
        TicketDetailResponse response = ticketService.getTicket(
            userDetails.getId(),
            ticketId
        );

        return ResponseEntity.ok(
            ApiResponse.success("티켓 상세 조회 완료", response)
        );
    }

    /**
     * 내 티켓 취소
     * <p>
     * POST /api/v1/tickets/{ticketId}/cancel
     *
     * @param userDetails 현재 로그인 사용자 정보
     * @param ticketId    취소할 티켓 ID
     * @return 티켓 취소 응답
     */
    @PostMapping("/{ticketId}/cancel")
    public ResponseEntity<ApiResponse<TicketCancelResponse>> cancelTicket(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long ticketId
    ) {
        TicketCancelResponse response = ticketService.cancelTicket(
            userDetails.getId(),
            ticketId
        );

        return ResponseEntity.ok(
            ApiResponse.success("티켓 취소 완료", response)
        );
    }
}
