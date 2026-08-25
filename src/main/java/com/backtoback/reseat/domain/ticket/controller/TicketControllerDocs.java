package com.backtoback.reseat.domain.ticket.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.ticket.dto.response.TicketCancelResponse;
import com.backtoback.reseat.domain.ticket.dto.response.TicketDetailResponse;
import com.backtoback.reseat.domain.ticket.dto.response.TicketListResponse;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.global.common.PageResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Ticket",
    description = "내 티켓 조회·취소 API"
)
@SecurityRequirement(name = "JWT Bearer Token")
public interface TicketControllerDocs {

    @Operation(
        summary = "내 티켓 목록 조회",
        description = """
            본인이 보유한 티켓을 상태(status)로 필터링해 페이지 단위로 조회합니다.
            status를 생략하면 전체 상태의 티켓을 조회합니다.
            """
    )
    @ApiResponses(
        {
            @ApiResponse(
                responseCode = "200",
                description = "내 티켓 목록 조회 성공"
            ),
            @ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST — status 값이 허용된 티켓 상태가 아님",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<PageResponse<TicketListResponse>>> getMyTickets(
        @Parameter(hidden = true) CustomUserDetails userDetails,
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
        @Parameter(description = "페이징 조건. page는 0부터 시작, 기본 페이지 크기 20") Pageable pageable
    );

    @Operation(
        summary = "내 티켓 상세 조회",
        description = "티켓 ID로 본인 소유 티켓의 좌석·QR 토큰·경기 일시 등 상세 정보를 조회합니다."
    )
    @ApiResponses(
        {
            @ApiResponse(
                responseCode = "200",
                description = "티켓 상세 조회 성공"
            ),
            @ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "403",
                description = "TICKET_ACCESS_DENIED — 본인 소유 티켓이 아님",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "TICKET_NOT_FOUND — 존재하지 않는 티켓",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<TicketDetailResponse>> getTicket(
        @Parameter(hidden = true) CustomUserDetails userDetails,
        @Parameter(
            description = "티켓 ID",
            example = "1001",
            required = true
        ) Long ticketId
    );

    @Operation(
        summary = "내 티켓 취소",
        description = """
            본인 소유 티켓을 취소하고 결제를 환불합니다. 경기 시작 24시간 전까지만 취소할 수 있습니다.
            취소 대상 티켓이 속한 주문의 결제 전체가 Toss 취소 API로 전액 환불되며,
            같은 주문에 포함된 다른 ISSUED 티켓이 있다면 함께 취소 처리됩니다(부분 환불 미지원).
            """
    )
    @ApiResponses(
        {
            @ApiResponse(
                responseCode = "200",
                description = "티켓 취소 완료"
            ),
            @ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "403",
                description = "TICKET_ACCESS_DENIED — 본인 소유 티켓이 아님",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "404",
                description = "TICKET_NOT_FOUND / PAYMENT_NOT_FOUND",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "409",
                description = "TICKET_CANCEL_DEADLINE_PASSED / TICKET_ALREADY_CANCELED / "
                    + "TICKET_ALREADY_USED / PAYMENT_CANCEL_NOT_ALLOWED",
                content = @Content
            ),
            @ApiResponse(
                responseCode = "502",
                description = "PAYMENT_CANCEL_FAILED — PG 취소 처리 실패",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<TicketCancelResponse>> cancelTicket(
        @Parameter(hidden = true) CustomUserDetails userDetails,
        @Parameter(
            description = "취소할 티켓 ID",
            example = "1001",
            required = true
        ) Long ticketId
    );
}
