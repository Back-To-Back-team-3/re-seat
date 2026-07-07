package com.backtoback.reseat.domain.payment.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentFailRequest {

    private String failReason;
}
