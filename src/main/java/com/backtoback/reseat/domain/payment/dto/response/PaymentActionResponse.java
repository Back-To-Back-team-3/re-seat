package com.backtoback.reseat.domain.payment.dto.response;

import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "결제 승인·실패·취소 처리 응답")
public class PaymentActionResponse {

    @Schema(description = "결제 ID", example = "1001")
    private final Long paymentId;

    @Schema(description = "처리 후 결제 상태", example = "APPROVED")
    private final PaymentStatus status;

    public static PaymentActionResponse from(Payment payment) {
        return PaymentActionResponse.builder()
            .paymentId(payment.getId())
            .status(payment.getStatus())
            .build();
    }
}
