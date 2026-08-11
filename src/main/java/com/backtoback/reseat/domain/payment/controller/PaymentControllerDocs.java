package com.backtoback.reseat.domain.payment.controller;

import org.springframework.http.ResponseEntity;

import com.backtoback.reseat.domain.payment.dto.request.PaymentCancelRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCompleteRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentFailRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentActionResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCreateResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentResponse;
import com.backtoback.reseat.global.security.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment", description = "결제 생성·승인·실패·취소·조회 API")
public interface PaymentControllerDocs {

	@Operation(
		summary = "결제 요청",
		description = """
			주문을 기준으로 Toss 결제 요청에 사용할 READY 결제를 생성하거나 기존 결제를 반환합니다.
			동일 Idempotency-Key 재요청은 최초 결과를 반환하고, 같은 주문의 READY 결제에 새로운 키가 전달되면 해당 키로 교체합니다.
			""",
		security = @SecurityRequirement(name = "JWT Bearer Token")
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "결제 요청 처리 성공"),
		@ApiResponse(
			responseCode = "400",
			description = "INVALID_REQUEST / IDEMPOTENCY_KEY_REQUIRED",
			content = @Content
		),
		@ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
		@ApiResponse(responseCode = "403", description = "PAYMENT_ACCESS_DENIED", content = @Content),
		@ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND", content = @Content),
		@ApiResponse(
			responseCode = "409",
			description = "IDEMPOTENCY_KEY_CONFLICT / INVALID_ORDER_STATUS / LOCK_FAILED",
			content = @Content
		),
		@ApiResponse(responseCode = "410", description = "ORDER_EXPIRED", content = @Content)
	})
	ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<PaymentCreateResponse>> requestPayment(
		@Parameter(hidden = true)
		CustomUserDetails userDetails,
		@Parameter(
			description = "결제 요청을 식별하는 멱등키",
			example = "8f14e45f-ea5e-4a2f-b3d2-09bcdb51f321",
			required = true
		)
		String idempotencyKey,
		PaymentRequest request
	);

	@Operation(summary = "결제 승인", description = """
		Toss 결제 인증 결과를 검증하고 승인 API를 호출해 결제를 확정합니다.
		승인 성공 시 결제와 주문을 완료 처리합니다.
		승인 상태를 확인할 수 없으면 결제를 실패 처리하고 자동 환불을 위한 복구 작업을 등록합니다.
		""", security = @SecurityRequirement(name = "JWT Bearer Token"))
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "결제 승인 처리 성공 또는 기존 종결 결과 반환"),
		@ApiResponse(
			responseCode = "400",
			description = "INVALID_REQUEST / IDEMPOTENCY_KEY_REQUIRED / PAYMENT_CALLBACK_MISMATCH",
			content = @Content
		),
		@ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
		@ApiResponse(responseCode = "403", description = "PAYMENT_ACCESS_DENIED", content = @Content),
		@ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND", content = @Content),
		@ApiResponse(
			responseCode = "409",
			description = "IDEMPOTENCY_KEY_UNAVAILABLE / INVALID_ORDER_STATUS",
			content = @Content),
		@ApiResponse(responseCode = "410", description = "ORDER_EXPIRED", content = @Content)
	})
	ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<PaymentActionResponse>> completePayment(
		@Parameter(hidden = true)
		CustomUserDetails userDetails,
		@Parameter(description = "결제 ID", example = "1001", required = true)
		Long paymentId,
		@Parameter(description = "현재 결제 시도의 활성 멱등키", example = "8f14e45f-ea5e-4a2f-b3d2-09bcdb51f321", required = true)
		String idempotencyKey,
		PaymentCompleteRequest request
	);

	@Operation(summary = "결제 실패 처리", description = """
		Toss 위젯 인증 실패 또는 사용자 중단 결과를 READY 결제에 반영합니다.
		Toss API는 호출하지 않으며 결제와 주문을 실패 상태로 전환합니다.
		""", security = @SecurityRequirement(name = "JWT Bearer Token"))
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "결제 실패 처리 성공 또는 기존 종결 결과 반환"),
		@ApiResponse(
			responseCode = "400",
			description = "INVALID_REQUEST / IDEMPOTENCY_KEY_REQUIRED / PAYMENT_CALLBACK_MISMATCH",
			content = @Content
		),
		@ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
		@ApiResponse(responseCode = "403", description = "PAYMENT_ACCESS_DENIED", content = @Content),
		@ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND", content = @Content),
		@ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_UNAVAILABLE", content = @Content)
	})
	ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<PaymentActionResponse>> failPayment(
		@Parameter(hidden = true)
		CustomUserDetails userDetails,
		@Parameter(description = "결제 ID", example = "1001", required = true)
		Long paymentId,
		@Parameter(description = "현재 결제 시도의 활성 멱등키", example = "8f14e45f-ea5e-4a2f-b3d2-09bcdb51f321", required = true)
		String idempotencyKey,
		PaymentFailRequest request);

	@Operation(summary = "결제 전액 취소", description = """
		승인된 결제를 Toss 취소 API로 전액 취소합니다.
		이미 취소된 결제는 Toss를 다시 호출하지 않고 기존 결과를 반환합니다.
		""", security = @SecurityRequirement(name = "JWT Bearer Token"))
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "결제 취소 처리 성공 또는 기존 취소 결과 반환"),
		@ApiResponse(responseCode = "400", description = "INVALID_REQUEST", content = @Content),
		@ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
		@ApiResponse(responseCode = "403", description = "PAYMENT_ACCESS_DENIED", content = @Content),
		@ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND", content = @Content),
		@ApiResponse(responseCode = "409", description = "PAYMENT_CANCEL_NOT_ALLOWED", content = @Content),
		@ApiResponse(responseCode = "502", description = "PAYMENT_CANCEL_FAILED", content = @Content)
	})
	ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<PaymentActionResponse>> cancelPayment(
		@Parameter(hidden = true)
		CustomUserDetails userDetails,
		@Parameter(description = "결제 ID", example = "1001", required = true)
		Long paymentId,
		PaymentCancelRequest request);

	@Operation(
		summary = "결제 단건 조회",
		description = "결제 ID로 본인의 결제 상세 상태와 처리 결과를 조회합니다.",
		security = @SecurityRequirement(name = "JWT Bearer Token")
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "결제 조회 성공"),
		@ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
		@ApiResponse(responseCode = "403", description = "PAYMENT_ACCESS_DENIED", content = @Content),
		@ApiResponse(responseCode = "404", description = "PAYMENT_NOT_FOUND", content = @Content)
	})
	ResponseEntity<com.backtoback.reseat.global.common.ApiResponse<PaymentResponse>> getPayment(
		@Parameter(hidden = true)
		CustomUserDetails userDetails,
		@Parameter(description = "결제 ID", example = "1001", required = true)
		Long paymentId);
}
