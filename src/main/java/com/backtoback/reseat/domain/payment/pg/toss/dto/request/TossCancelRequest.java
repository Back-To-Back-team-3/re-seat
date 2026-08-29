package com.backtoback.reseat.domain.payment.pg.toss.dto.request;

/** Toss 결제 취소 요청 정보를 전달한다. */
public record TossCancelRequest(String cancelReason, Integer cancelAmount) {
}
