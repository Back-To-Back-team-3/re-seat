package com.backtoback.reseat.domain.payment.controller;

import com.backtoback.reseat.domain.payment.dto.request.PaymentCompleteRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentFailRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentActionResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCreateResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentResponse;
import com.backtoback.reseat.domain.payment.service.PaymentService;
import com.backtoback.reseat.global.common.ApiResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentCreateResponse>> requestPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request
    ) {
        PaymentCreateResponse response = paymentService.requestPayment(
                userDetails.getId(),
                idempotencyKey,
                request
        );

        return ResponseEntity.ok(ApiResponse.success("결제 요청 처리 완료", response));
    }

    @PostMapping("/{paymentId}/complete")
    public ResponseEntity<ApiResponse<PaymentActionResponse>> completePayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentCompleteRequest request
    ) {
        PaymentActionResponse response = paymentService.completePayment(
                userDetails.getId(),
                paymentId,
                request
        );

        return ResponseEntity.ok(ApiResponse.success("결제 승인 처리 완료", response));
    }

    @PostMapping("/{paymentId}/fail")
    public ResponseEntity<ApiResponse<PaymentActionResponse>> failPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paymentId,
            @Valid @RequestBody PaymentFailRequest request
    ) {
        PaymentActionResponse response = paymentService.failPayment(
                userDetails.getId(),
                paymentId,
                request
        );

        return ResponseEntity.ok(ApiResponse.success("결제 실패 처리 완료", response));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paymentId
    ) {
        PaymentResponse response = paymentService.getPayment(userDetails.getId(), paymentId);

        return ResponseEntity.ok(ApiResponse.success("결제 조회 완료", response));
    }
}
