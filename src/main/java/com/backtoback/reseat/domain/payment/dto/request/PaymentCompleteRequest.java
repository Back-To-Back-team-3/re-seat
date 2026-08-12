package com.backtoback.reseat.domain.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "결제 승인 요청")
public class PaymentCompleteRequest {

	@Schema(
	    description = "Toss 결제 인증 후 발급된 결제 키",
	    example = "tgen_20260725120000AbCdE",
	    requiredMode = Schema.RequiredMode.REQUIRED
	)
	@NotBlank(message = "paymentKey는 필수입니다.")
	private String paymentKey;

	@Schema(
	    description = "Toss 결제 인증에 사용된 주문 번호",
	    example = "ORD-20260725-A1B2C3",
	    requiredMode = Schema.RequiredMode.REQUIRED
	)
	@NotBlank(message = "orderId는 필수입니다.")
	private String orderId;

	@Schema(
	    description = "승인할 결제 금액",
	    example = "34000",
	    requiredMode = Schema.RequiredMode.REQUIRED
	)
	@NotNull(message = "결제 금액은 필수입니다.")
	@Positive(message = "결제 금액은 0보다 커야 합니다.")
	private Integer amount;
}
