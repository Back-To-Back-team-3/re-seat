package com.backtoback.reseat.domain.order.controller;

import com.backtoback.reseat.domain.order.dto.request.OrderCreateRequest;
import com.backtoback.reseat.domain.order.dto.response.OrderCancelResponse;
import com.backtoback.reseat.domain.order.dto.response.OrderResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Order", description = "주문 생성 · 조회 · 취소 API")
public interface OrderControllerDocs {

    @Operation(
        summary = "주문 생성",
        description = "선점한 좌석을 바탕으로 주문을 생성하고 결제 기한과 주문 정보를 반환합니다.",
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "주문 생성"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "INVALID_REQUEST",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "403",
            description = "RESERVATION_ACCESS_DENIED",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "404",
            description = "USER_NOT_FOUND / RESERVATION_NOT_FOUND / RESERVATION_SEAT_NOT_FOUND",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "409",
            description = "INVALID_RESERVATION_STATUS / RESERVATION_ALREADY_ORDERED",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "410",
            description = "PRE_RESERVATION_EXPIRED",
            content = @Content
        )
    })
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<OrderResponse>> createOrder(
        OrderCreateRequest request,
        @Parameter(hidden = true) CustomUserDetails userDetails
    );

    @Operation(
        summary = "주문 조회",
        description = "주문 ID로 본인의 주문 상세 정보를 조회합니다.",
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "주문 조회"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "INVALID_REQUEST",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "403",
            description = "FORBIDDEN",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "404",
            description = "ORDER_NOT_FOUND",
            content = @Content
        )
    })
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<OrderResponse>> getOrder(
        @Parameter(
            description = "주문 ID",
            example = "7001",
            required = true
        )
        Long orderId,
        @Parameter(hidden = true) CustomUserDetails userDetails
    );

    @Operation(
        summary = "주문 취소",
        description = "결제 전 주문을 취소하고 함께 선점된 좌석을 해제합니다.",
        security = @SecurityRequirement(name = "JWT Bearer Token")
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "주문 취소"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "INVALID_REQUEST",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "403",
            description = "FORBIDDEN",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "404",
            description = "ORDER_NOT_FOUND",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "409",
            description = "INVALID_ORDER_STATUS",
            content = @Content
        )
    })
    ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<OrderCancelResponse>> cancelOrder(
        @Parameter(
            description = "주문 ID",
            example = "7001",
            required = true
        )
        Long orderId,
        @Parameter(hidden = true) CustomUserDetails userDetails
    );
}
