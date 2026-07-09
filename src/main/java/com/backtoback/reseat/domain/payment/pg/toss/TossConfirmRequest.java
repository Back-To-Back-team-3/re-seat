package com.backtoback.reseat.domain.payment.pg.toss;

public record TossConfirmRequest(
        String paymentKey,
        String orderId,
        Integer amount
) {
}
