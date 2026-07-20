package com.backtoback.reseat.domain.ticket.admin.controller;

import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminUserTicketResponse;
import com.backtoback.reseat.domain.ticket.admin.service.AdminTicketService;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/tickets")
@RequiredArgsConstructor
public class AdminTicketController {

    private final AdminTicketService adminTicketService;
    //관리자 전용: 특정 사용자별 티켓 소유 목록 조회
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserTicketResponse>>> getUserTickets(
            @PathVariable Long userId,
            @RequestParam(required = false) TicketStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<AdminUserTicketResponse> pageResult = adminTicketService.getUserTickets(userId, status, pageable);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("사용자 티켓 소유 목록 조회 완료", PageResponse.of(pageResult)));
    }
}
