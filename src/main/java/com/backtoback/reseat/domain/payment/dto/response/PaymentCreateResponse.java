package com.backtoback.reseat.domain.payment.dto.response;

import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentCreateResponse {

    private final Long paymentId;
    private final String paymentNo;
    private final Long orderId;
    private final Integer amount;
    private final String method;
    private final PaymentStatus status;
    private final PgProvider pgProvider;
    private final String pgOrderId;

    public static PaymentCreateResponse from(Payment payment) {
        return PaymentCreateResponse.builder()
                .paymentId(payment.getId())
                .paymentNo(payment.getPaymentNo())
                .orderId(payment.getOrder().getId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .pgProvider(payment.getPgProvider())
                .pgOrderId(payment.getPgOrderId())
                .build();
    }
}
