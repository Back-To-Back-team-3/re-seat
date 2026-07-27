package com.backtoback.reseat.domain.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "결제 전액 취소 요청")
public class PaymentCancelRequest {

    @Schema(
            description = "Toss 취소 요청에 전달할 사유",
            example = "사용자 요청에 따른 예매 취소",
            maxLength = 200,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "취소 사유는 필수입니다.")
    @Size(max = 200, message = "취소 사유는 200자 이하여야 합니다.")
    private String cancelReason;
}
