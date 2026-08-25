package com.backtoback.reseat.domain.ticket.admin.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.ticket.admin.dto.request.AdminTicketCancelRequest;
import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminTicketCancelResponse;
import com.backtoback.reseat.domain.ticket.admin.dto.response.AdminUserTicketResponse;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.global.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Admin - Ticket",
    description = "관리자 티켓 관리 API (ROLE_ADMIN 전용)"
)
@SecurityRequirement(name = "JWT Bearer Token")
public interface AdminTicketControllerDocs {

    @Operation(
        summary = "특정 사용자 티켓 소유 목록 조회",
        description = "관리자가 특정 사용자가 보유한 티켓 목록을 상태로 필터링해 페이지 단위로 조회합니다."
    )
    @ApiResponses(
        {
            @ApiResponse(
                responseCode = "200",
                description = "사용자 티켓 소유 목록 조회 성공"
            ),
            @ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "403",
                description = "FORBIDDEN — 관리자 권한 없음",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND — 존재하지 않는 회원",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<PageResponse<AdminUserTicketResponse>>> getUserTickets(
        @Parameter(
            description = "조회 대상 회원 ID",
            example = "1",
            required = true
        ) Long userId,
        @Parameter(
            description = "티켓 상태 필터. 생략 시 전체 상태 조회",
            example = "ISSUED",
            schema = @Schema(
                allowableValues = {
                    "ISSUED",
                    "USED",
                    "CANCELED"
                }
            )
        ) TicketStatus status,
        @Parameter(description = "페이징 조건. page는 0부터 시작, 기본 페이지 크기 20, 기본 정렬 createdAt DESC") Pageable pageable
    );

    @Operation(
        summary = "티켓 강제 취소",
        description = """
            관리자 직권으로 티켓을 강제 취소하고 연관 좌석·결제를 함께 정리합니다.
            사용자 취소와 동일하게 Toss 취소 API를 호출해 결제를 전액 환불하며, 경기 시작 24시간 전 취소 기한의 제약을 받지 않습니다.
            같은 주문에 포함된 다른 ISSUED 티켓이 있다면 함께 취소 처리됩니다(부분 환불 미지원).
            """
    )
    @ApiResponses(
        {
            @ApiResponse(
                responseCode = "200",
                description = "관리자 직권 티켓 강제 취소 및 자원 반환 완료"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST — 취소 사유(reason) 누락",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "403",
                description = "FORBIDDEN — 관리자 권한 없음",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "TICKET_NOT_FOUND — 존재하지 않는 티켓",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "409",
                description = "TICKET_ALREADY_CANCELED / TICKET_ALREADY_USED / PAYMENT_CANCEL_NOT_ALLOWED",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "502",
                description = "PAYMENT_CANCEL_FAILED — PG 취소 처리 실패",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<AdminTicketCancelResponse>> cancelTicketByAdmin(
        @Parameter(
            description = "강제 취소할 티켓 ID",
            example = "1001",
            required = true
        ) Long ticketId,
        AdminTicketCancelRequest request
    );
}
