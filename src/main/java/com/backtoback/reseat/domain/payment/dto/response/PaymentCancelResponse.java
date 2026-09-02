package com.backtoback.reseat.domain.payment.dto.response;

import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentCancel;
import com.backtoback.reseat.domain.payment.entity.PaymentCancelStatus;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "결제 취소 응답")
public class PaymentCancelResponse {

    @Schema(
        description = "결제 ID",
        example = "5501"
    )
    private final Long paymentId;

    @Schema(
        description = "결제 취소 이력 ID",
        example = "8801"
    )
    private final Long paymentCancelId;

    @Schema(
        description = "결제 취소 처리 상태",
        example = "PENDING"
    )
    private final PaymentCancelStatus cancelStatus;

    @Schema(
        description = "이번 취소 환불 금액",
        example = "18000"
    )
    private final Integer refundAmount;

    @Schema(
        description = "누적 취소 금액",
        example = "18000"
    )
    private final Integer canceledAmount;

    @Schema(
        description = "취소 후 남은 결제 금액",
        example = "0"
    )
    private final Integer remainingAmount;

    @Schema(
        description = "취소 후 결제 상태",
        example = "CANCELED"
    )
    private final PaymentStatus paymentStatus;

    public static PaymentCancelResponse from(Payment payment) {
        return PaymentCancelResponse
            .builder()
            .paymentId(payment.getId())
            .refundAmount(payment.getAmount())
            .canceledAmount(payment.getAmount())
            .remainingAmount(0)
            .paymentStatus(payment.getStatus())
            .build();
    }

    /** 티켓 단위 취소 접수 또는 처리 결과를 응답으로 변환한다. */
    public static PaymentCancelResponse from(Payment payment, PaymentCancel paymentCancel) {
        int refundAmount = paymentCancel.getTicket().getOrderItem().getPrice();
        return PaymentCancelResponse
            .builder()
            .paymentId(payment.getId())
            .paymentCancelId(paymentCancel.getId())
            .cancelStatus(paymentCancel.getStatus())
            .refundAmount(refundAmount)
            .canceledAmount(payment.getCanceledAmount())
            .remainingAmount(payment.getRemainingAmount())
            .paymentStatus(payment.getStatus())
            .build();
    }
}
