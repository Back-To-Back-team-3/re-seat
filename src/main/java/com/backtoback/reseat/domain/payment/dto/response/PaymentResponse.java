package com.backtoback.reseat.domain.payment.dto.response;

import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "결제 상세 조회 응답")
public class PaymentResponse {

    @Schema(description = "결제 ID", example = "1001")
    private final Long paymentId;

    @Schema(description = "서비스 내부 결제 번호", example = "PAY-20260725120000-a1b2c3d4")
    private final String paymentNo;

    @Schema(description = "주문 ID", example = "1001")
    private final Long orderId;

    @Schema(description = "결제 금액", example = "34000")
    private final Integer amount;

    @Schema(description = "결제 수단. 승인 전에는 null", example = "CARD", nullable = true)
    private final String method;

    @Schema(description = "결제 상태", example = "APPROVED")
    private final PaymentStatus status;

    @Schema(description = "PG 제공사", example = "TOSS")
    private final PgProvider pgProvider;

    @Schema(description = "결제 실패 사유. 실패하지 않았다면 null", nullable = true)
    private final String failReason;

    @Schema(
        description = "결제 승인 시각. 승인되지 않았다면 null",
        example = "2026-07-25T12:00:00",
        nullable = true
    )
    private final LocalDateTime approvedAt;

    @Schema(
        description = "결제 실패 시각. 실패하지 않았다면 null",
        example = "2026-07-25T12:00:00",
        nullable = true
    )
    private final LocalDateTime failedAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
            .paymentId(payment.getId())
            .paymentNo(payment.getPaymentNo())
            .orderId(payment.getOrder().getId())
            .amount(payment.getAmount())
            .method(payment.getMethod())
            .status(payment.getStatus())
            .pgProvider(payment.getPgProvider())
            .failReason(payment.getFailReason())
            .approvedAt(payment.getApprovedAt())
            .failedAt(payment.getFailedAt())
            .build();
    }
}
