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
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 사용자 티켓 API Swagger 문서화 인터페이스.
 */
@Tag(
    name = "Ticket",
    description = "내 티켓 조회·취소·재시도 API"
)
public interface TicketControllerDocs {

    @Operation(
        summary = "내 티켓 목록 조회",
        description = "로그인한 사용자 본인의 티켓 목록을 상태 필터·페이징으로 조회한다.",
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<PageResponse<TicketListResponse>>> getMyTickets(
        @Parameter(hidden = true) CustomUserDetails userDetails,
        @Parameter(
            description = "티켓 상태 필터",
            example = "ISSUED"
        ) TicketStatus status,
        Pageable pageable
    );

    @Operation(
        summary = "내 티켓 상세 조회",
        description = "티켓 1건의 경기·좌석·환불 가능 여부 등 상세 정보를 조회한다. 본인 소유 티켓만 조회할 수 있다.",
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "TICKET_ACCESS_DENIED — 타인 소유 티켓",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "TICKET_NOT_FOUND",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<TicketDetailResponse>> getTicket(
        @Parameter(hidden = true) CustomUserDetails userDetails,
        @Parameter(
            description = "티켓 ID",
            example = "9051",
            required = true
        ) Long ticketId
    );

    @Operation(
        summary = "내 티켓 취소",
        description = """
            보유 티켓 1장을 취소(환불) 접수한다. ISSUED 상태이고 경기 시작 24시간 전인 경우만 가능하다.
            PG 취소는 비동기로 처리되므로, 이 API는 REFUND_PENDING 접수 결과만 즉시 반환한다.
            """,
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "취소 접수 성공(REFUND_PENDING)",
                content = @Content(
                    examples = @ExampleObject(
                        name = "취소 접수 성공 예시",
                        value = """
                            {
                                "success": true,
                                "errorCode": null,
                                "message": "티켓 취소 요청 접수 완료",
                                "data": {
                                    "ticketId": 9051,
                                    "ticketStatus": "REFUND_PENDING",
                                    "refundRequestedAt": "2026-09-12 15:00:00"
                                }
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "TICKET_ACCESS_DENIED — 타인 소유 티켓",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "TICKET_NOT_FOUND",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "TICKET_CANCEL_DEADLINE_PASSED(환불 기한 경과) / TICKET_ALREADY_USED(입장·미입장 완료) "
                    + "/ TICKET_REFUND_IN_PROGRESS(이미 REFUND_PENDING) / TICKET_ALREADY_REFUNDED(이미 환불 완료) "
                    + "/ TICKET_REFUND_FAILED_RETRY_REQUIRED(REFUND_FAILED, 재시도 필요)",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<TicketCancelResponse>> cancelTicket(
        @Parameter(hidden = true) CustomUserDetails userDetails,
        @Parameter(
            description = "취소할 티켓 ID",
            example = "9051",
            required = true
        ) Long ticketId
    );

    @Operation(
        summary = "내 티켓 취소 재시도",
        description = "REFUND_FAILED 상태의 티켓을 다시 REFUND_PENDING으로 되돌리고 취소 파이프라인에 재등록한다.",
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "재시도 접수 성공(REFUND_PENDING)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "TICKET_ACCESS_DENIED — 타인 소유 티켓",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "TICKET_NOT_FOUND",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "INVALID_STATE_TRANSITION — REFUND_FAILED 상태가 아닌 티켓에 재시도",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<TicketCancelResponse>> retryCancelTicket(
        @Parameter(hidden = true) CustomUserDetails userDetails,
        @Parameter(
            description = "재시도할 티켓 ID",
            example = "9051",
            required = true
        ) Long ticketId
    );
}
