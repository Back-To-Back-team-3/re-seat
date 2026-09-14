package com.backtoback.reseat.domain.admin.ticket.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.admin.ticket.dto.request.AdminTicketBulkCancelRequest;
import com.backtoback.reseat.domain.admin.ticket.dto.request.AdminTicketCancelRequest;
import com.backtoback.reseat.domain.admin.ticket.dto.request.TicketSearchCondition;
import com.backtoback.reseat.domain.admin.ticket.dto.response.AdminTicketBulkCancelResponse;
import com.backtoback.reseat.domain.admin.ticket.dto.response.AdminTicketCancelResponse;
import com.backtoback.reseat.domain.admin.ticket.dto.response.AdminTicketQrReissueResponse;
import com.backtoback.reseat.domain.admin.ticket.dto.response.AdminUserTicketResponse;
import com.backtoback.reseat.domain.admin.ticket.service.AdminTicketService;
import com.backtoback.reseat.domain.ticket.dto.request.TicketVerifyRequest;
import com.backtoback.reseat.domain.ticket.dto.response.TicketVerifyResponse;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.service.TicketService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.common.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/tickets")
@RequiredArgsConstructor
public class AdminTicketController implements AdminTicketControllerDocs {

    private final AdminTicketService adminTicketService;
    private final TicketService ticketService; // QR 검표 전용

    // 관리자 전용: 특정 사용자별 티켓 소유 목록 조회
    @Override
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserTicketResponse>>> getUserTickets(
        @PathVariable Long userId,
        @RequestParam(required = false) TicketStatus status,
        @PageableDefault(
            size = 20,
            sort = "createdAt",
            direction = Sort.Direction.DESC
        ) Pageable pageable
    ) {

        Page<AdminUserTicketResponse> pageResult = adminTicketService.getUserTickets(userId, status, pageable);

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("사용자 티켓 소유 목록 조회 완료", PageResponse.of(pageResult)));
    }

    // 관리자 전용: 특정 티켓 강제 취소
    @Override
    @PostMapping("/{ticketId}/cancel")
    public ResponseEntity<ApiResponse<AdminTicketCancelResponse>> cancelTicketByAdmin(
        @PathVariable Long ticketId,
        @Valid @RequestBody AdminTicketCancelRequest request
    ) {

        AdminTicketCancelResponse response = adminTicketService.cancelTicketByAdmin(ticketId, request);

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("관리자 직권 티켓 강제 취소 요청 접수 완료", response));
    }

    // 관리자 전용: QR 검표(입장 처리)
    @Override
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<TicketVerifyResponse>> verifyTicket(
        @Valid @RequestBody TicketVerifyRequest request
    ) {

        TicketVerifyResponse response = ticketService.verify(request.getGameId(), request.getQrToken());

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("QR 검표 완료", response));
    }

    // 관리자 전용: QR 토큰 재발급
    @Override
    @PostMapping("/{ticketId}/qr/reissue")
    public ResponseEntity<ApiResponse<AdminTicketQrReissueResponse>> reissueQrToken(@PathVariable Long ticketId) {

        AdminTicketQrReissueResponse response = adminTicketService.reissueQrToken(ticketId);

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("QR 재발급 완료", response));
    }

    // 관리자 전용: 회원·날짜·상태 통합 검색
    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminUserTicketResponse>>> searchTickets(
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) TicketStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate gameDateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate gameDateTo,
        @PageableDefault(
            size = 20,
            sort = "issuedAt",
            direction = Sort.Direction.DESC
        ) Pageable pageable
    ) {

        TicketSearchCondition condition = new TicketSearchCondition(userId, status, gameDateFrom, gameDateTo);
        Page<AdminUserTicketResponse> pageResult = adminTicketService.searchTickets(condition, pageable);

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("티켓 통합 검색 완료", PageResponse.of(pageResult)));
    }

    // 관리자 전용: 경기 단위 ISSUED 티켓 일괄 취소
    @Override
    @PostMapping("/games/{gameId}/cancel-bulk")
    public ResponseEntity<ApiResponse<AdminTicketBulkCancelResponse>> cancelTicketsByGame(
        @PathVariable Long gameId,
        @Valid @RequestBody AdminTicketBulkCancelRequest request
    ) {

        AdminTicketBulkCancelResponse response = adminTicketService.cancelTicketsByGame(gameId, request.reason());

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success("경기 일괄 취소 처리 완료", response));
    }
}
