package com.backtoback.reseat.domain.payment.dto.response;

import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentActionResponse {

    private final Long paymentId;
    private final PaymentStatus status;

    public static PaymentActionResponse from(Payment payment) {
        return PaymentActionResponse.builder()
                .paymentId(payment.getId())
                .status(payment.getStatus())
                .build();
    }
}
