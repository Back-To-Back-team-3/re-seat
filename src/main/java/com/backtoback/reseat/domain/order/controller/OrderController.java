package com.backtoback.reseat.domain.order.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backtoback.reseat.domain.order.dto.request.OrderCreateRequest;
import com.backtoback.reseat.domain.order.dto.response.OrderCancelResponse;
import com.backtoback.reseat.domain.order.dto.response.OrderResponse;
import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 주문 API Controller
 *
 * <p>예약 기반 주문 생성, 주문 조회, 주문 취소 API를 제공한다.</p>
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController implements OrderControllerDocs {

    private final OrderService orderService;

    /**
     * 6.1 주문 생성
     *
     * @param request     주문 생성 요청
     * @param userDetails JWT 인증 사용자
     * @return 생성된 주문 정보
     */
    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
        @Valid @RequestBody OrderCreateRequest request,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getId();
        OrderResponse response = orderService.createOrder(userId, request.getReservationId());

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("주문 생성", response));
    }

    /**
     * 6.2 주문 조회
     *
     * @param orderId     조회할 주문 ID
     * @param userDetails JWT 인증 사용자
     * @return 주문 상세 정보
     */
    @Override
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
        @PathVariable Long orderId,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getId();
        OrderResponse response = orderService.getOrder(userId, orderId);

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("주문 조회", response));
    }

    /**
     * 6.3 주문 취소
     *
     * @param orderId     취소할 주문 ID
     * @param userDetails JWT 인증 사용자
     * @return 취소된 주문 정보
     */
    @Override
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderCancelResponse>> cancelOrder(
        @PathVariable Long orderId,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        Long userId = userDetails.getId();
        OrderCancelResponse response = orderService.cancelOrder(userId, orderId);

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(ApiResponse.success("주문 취소", response));
    }
}
