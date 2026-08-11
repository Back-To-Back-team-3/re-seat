package com.backtoback.reseat.domain.payment.pg.toss.dto.request;

public record TossConfirmRequest(
	String paymentKey,
	String orderId,
	Integer amount) {
}
