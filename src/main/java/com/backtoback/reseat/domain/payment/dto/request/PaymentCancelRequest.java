package com.backtoback.reseat.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCancelRequest {

    @NotBlank(message = "취소 사유는 필수입니다.")
    @Size(max = 200, message = "취소 사유는 200자 이하여야 합니다.")
    private String cancelReason;
}
