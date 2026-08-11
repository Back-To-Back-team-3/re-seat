package com.backtoback.reseat.domain.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "결제 인증 실패 요청")
public class PaymentFailRequest {

	@Schema(
		description = "Toss가 전달한 결제 실패 코드",
		example = "PAY_PROCESS_CANCELED",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	@NotBlank(message = "실패 코드는 필수입니다.")
	private String code;

	@Schema(
		description = "Toss가 전달한 결제 실패 메시지",
		example = "사용자가 결제를 취소했습니다.",
		requiredMode = Schema.RequiredMode.REQUIRED)
	@NotBlank(message = "실패 메시지는 필수입니다."
	)
	private String message;

	@Schema(
		description = "Toss 결제 인증에 사용된 주문 번호",
		example = "ORD-20260725-A1B2C3",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	@NotBlank(message = "orderId는 필수입니다.")
	private String orderId;
}
