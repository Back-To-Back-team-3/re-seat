package com.backtoback.reseat.domain.admin.ticket.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.admin.ticket.dto.request.AdminTicketBulkCancelRequest;
import com.backtoback.reseat.domain.admin.ticket.dto.request.AdminTicketCancelRequest;
import com.backtoback.reseat.domain.admin.ticket.dto.response.AdminTicketBulkCancelResponse;
import com.backtoback.reseat.domain.admin.ticket.dto.response.AdminTicketCancelResponse;
import com.backtoback.reseat.domain.admin.ticket.dto.response.AdminTicketQrReissueResponse;
import com.backtoback.reseat.domain.admin.ticket.dto.response.AdminUserTicketResponse;
import com.backtoback.reseat.domain.ticket.dto.request.TicketVerifyRequest;
import com.backtoback.reseat.domain.ticket.dto.response.TicketVerifyResponse;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.global.common.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 관리자용 티켓 관리 API Swagger 문서화 인터페이스.
 * <p>ROLE_ADMIN 전용 API이므로 JWT Bearer Token @SecurityRequirement만 선언한다.
 * ADMIN 권한 검증은 Spring Security가 처리한다.
 */
@Tag(
    name = "Admin - Ticket",
    description = "티켓 강제 취소·QR 검표/재발급·통합 검색·경기별 일괄 취소 API (ROLE_ADMIN 전용)"
)
public interface AdminTicketControllerDocs {

    @Operation(
        summary = "사용자 티켓 소유 목록 조회",
        description = "ROLE_ADMIN 필요. 특정 회원이 보유한 티켓 목록을 상태 필터·페이징으로 조회한다.",
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
                description = "ADMIN 권한 없음",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<PageResponse<AdminUserTicketResponse>>> getUserTickets(
        @Parameter(description = "회원 ID", example = "1001", required = true) Long userId,
        @Parameter(description = "티켓 상태 필터", example = "ISSUED") TicketStatus status,
        Pageable pageable
    );

    @Operation(
        summary = "티켓 강제 취소",
        description = "ROLE_ADMIN 필요. 소유자·환불 기한 검증 없이 특정 티켓을 관리자 직권으로 취소 파이프라인에 등록한다.",
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "취소 접수 성공(REFUND_PENDING)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST — 취소 사유 누락",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "ADMIN 권한 없음",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "TICKET_NOT_FOUND",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "TICKET_ALREADY_USED / TICKET_REFUND_IN_PROGRESS / TICKET_ALREADY_REFUNDED "
                    + "/ TICKET_REFUND_FAILED_RETRY_REQUIRED",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<AdminTicketCancelResponse>> cancelTicketByAdmin(
        @Parameter(description = "취소할 티켓 ID", example = "9051", required = true) Long ticketId,
        AdminTicketCancelRequest request
    );

    @Operation(
        summary = "QR 검표(입장 처리)",
        description = "ROLE_ADMIN 필요. QR 토큰과 경기 ID로 티켓을 조회해 입장 처리한다. ISSUED 상태의 티켓만 가능하다.",
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "검표 성공",
                content = @Content(
                    examples = @ExampleObject(
                        name = "검표 성공 예시",
                        value = """
                            {
                                "success": true,
                                "errorCode": null,
                                "message": "QR 검표 완료",
                                "data": {
                                    "ticketId": 9051,
                                    "status": "USED_ENTERED",
                                    "usedAt": "2026-09-12T18:24:03",
                                    "seat": "1루 블루석 A-3-12",
                                    "holderName": "이서연"
                                }
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST — qrToken/gameId 누락",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "ADMIN 권한 없음",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "TICKET_NOT_FOUND — qrToken+gameId 조합의 티켓 없음",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "TICKET_REFUND_IN_PROGRESS / TICKET_REFUND_FAILED_RETRY_REQUIRED "
                    + "/ TICKET_ALREADY_REFUNDED / TICKET_ALREADY_USED(재검표)",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<TicketVerifyResponse>> verifyTicket(
        TicketVerifyRequest request
    );

    @Operation(
        summary = "QR 토큰 재발급",
        description = "ROLE_ADMIN 필요. 분실·유출된 QR 토큰을 새로 발급한다. ISSUED 상태의 티켓만 가능하다.",
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "재발급 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "ADMIN 권한 없음",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "TICKET_NOT_FOUND",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "INVALID_STATE_TRANSITION — ISSUED 상태가 아닌 티켓",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<AdminTicketQrReissueResponse>> reissueQrToken(
        @Parameter(description = "티켓 ID", example = "9051", required = true) Long ticketId
    );

    @Operation(
        summary = "티켓 통합 검색",
        description = "ROLE_ADMIN 필요. 회원·경기 날짜 범위·상태를 조합해 검색한다. 모든 조건은 선택이며, "
            + "REFUND_FAILED 목록 조회도 status=REFUND_FAILED로 이 API를 사용한다.",
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
                description = "ADMIN 권한 없음",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND — userId 지정했으나 존재하지 않는 회원",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<PageResponse<AdminUserTicketResponse>>> searchTickets(
        @Parameter(description = "회원 ID(선택)", example = "1001") Long userId,
        @Parameter(description = "티켓 상태 필터(선택)", example = "REFUND_FAILED") TicketStatus status,
        @Parameter(description = "경기일 시작(선택)", example = "2026-09-01") LocalDate gameDateFrom,
        @Parameter(description = "경기일 끝(선택)", example = "2026-09-30") LocalDate gameDateTo,
        Pageable pageable
    );

    @Operation(
        summary = "경기별 일괄 강제 취소",
        description = """
            ROLE_ADMIN 필요. 특정 경기의 ISSUED 티켓 전체를 관리자 직권 취소 파이프라인에 등록한다.
            티켓 1건당 독립된 트랜잭션으로 처리되어, 일부 실패가 나머지 처리에 영향을 주지 않는다.
            개별 티켓 실패는 HTTP 에러가 아니라 200 응답의 results 배열에 담겨 반환된다.
            """,
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses(
        {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "처리 완료(개별 성공/실패는 응답 본문 참고)",
                content = @Content(
                    examples = @ExampleObject(
                        name = "일부 실패 포함 예시",
                        value = """
                            {
                                "success": true,
                                "errorCode": null,
                                "message": "경기 일괄 취소 처리 완료",
                                "data": {
                                    "totalCount": 12,
                                    "successCount": 10,
                                    "failureCount": 2,
                                    "results": [
                                        { "ticketId": 9051, "success": true, "message": "취소 접수 완료" },
                                        { "ticketId": 9052, "success": false, "message": "이미 환불 완료된 티켓입니다." }
                                    ]
                                }
                            }
                            """
                    )
                )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "INVALID_REQUEST — 취소 사유 누락",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 실패",
                content = @Content
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "ADMIN 권한 없음",
                content = @Content
            )
        }
    )
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<AdminTicketBulkCancelResponse>> cancelTicketsByGame(
        @Parameter(description = "경기 ID", example = "1042", required = true) Long gameId,
        AdminTicketBulkCancelRequest request
    );
}
