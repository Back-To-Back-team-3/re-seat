package com.backtoback.reseat.domain.order.controller;

import com.backtoback.reseat.domain.order.dto.request.OrderCreateRequest;
import com.backtoback.reseat.domain.order.dto.response.OrderResponse;
import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 API Controller
 *
 * <p>예약 선점 정보를 바탕으로 주문을 생성하는 API를 제공한다.</p>
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 6.1 주문 생성
     *
     * @param request 주문 생성 요청
     * @param userDetails JWT 인증 사용자
     * @return 생성된 주문 정보
     */
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
}
