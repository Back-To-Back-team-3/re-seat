package com.backtoback.reseat.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentFailRequest {

    @NotBlank(message = "실패 코드는 필수입니다.")
    private String code;

    @NotBlank(message = "실패 메시지는 필수입니다.")
    private String message;

    @NotBlank(message = "orderId는 필수입니다.")
    private String orderId;
}
