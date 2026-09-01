package com.backtoback.reseat.domain.payment.pg.toss.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Toss 결제 취소 요청 정보를 전달한다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TossCancelRequest(String cancelReason, Integer cancelAmount) {
}
