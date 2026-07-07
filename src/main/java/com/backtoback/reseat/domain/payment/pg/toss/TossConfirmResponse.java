package com.backtoback.reseat.domain.payment.pg.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 토스 실제 승인 응답은 card/receipt/easyPay 등 필드가 훨씬 많음 - 여기서 쓰는 필드만 매핑하고 나머지는 무시한다.
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossConfirmResponse {

    private String paymentKey;
    private String orderId;
    private String status;
    private String approvedAt;
    private String method;
}
