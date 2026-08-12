package com.backtoback.reseat.domain.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "결제 요청")
public class PaymentRequest {

	@Schema(
		description = "결제할 주문 ID",
		example = "1001",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	@NotNull(message = "주문 ID는 필수입니다.")
	private Long orderId;
}
