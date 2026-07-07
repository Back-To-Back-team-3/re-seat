package com.backtoback.reseat.domain.payment.dto.response;

import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentMethod;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentResponse {

    private final Long paymentId;
    private final String paymentNo;
    private final Long orderId;
    private final Integer amount;
    private final PaymentMethod method;
    private final PaymentStatus status;
    private final PgProvider pgProvider;
    private final String pgOrderId;
    private final String pgPaymentKey;
    private final String failReason;
    private final LocalDateTime approvedAt;
    private final LocalDateTime failedAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .paymentNo(payment.getPaymentNo())
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .pgProvider(payment.getPgProvider())
                .pgOrderId(payment.getPgOrderId())
                .pgPaymentKey(payment.getPgPaymentKey())
                .failReason(payment.getFailReason())
                .approvedAt(payment.getApprovedAt())
                .failedAt(payment.getFailedAt())
                .build();
    }
}
