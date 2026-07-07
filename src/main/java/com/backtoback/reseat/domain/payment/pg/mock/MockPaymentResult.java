package com.backtoback.reseat.domain.payment.pg.mock;

import java.time.LocalDateTime;

public record MockPaymentResult(
        boolean approved,
        String pgPaymentKey,
        String failReason,
        LocalDateTime requestedAt
) {

    public static MockPaymentResult approved(String pgPaymentKey, LocalDateTime approvedAt) {
        return new MockPaymentResult(true, pgPaymentKey, null, approvedAt);
    }

    public static MockPaymentResult failed(String failReason, LocalDateTime failedAt) {
        return new MockPaymentResult(false, null, failReason, failedAt);
    }
}
