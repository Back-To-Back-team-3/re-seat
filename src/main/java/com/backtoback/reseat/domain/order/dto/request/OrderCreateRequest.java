package com.backtoback.reseat.domain.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 생성 요청 DTO
 */
@Getter
@NoArgsConstructor
@Schema(description = "주문 생성 요청")
public class OrderCreateRequest {

    @Schema(
            description = "주문으로 전활할 예약 ID",
            example = "1001",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "예약 ID는 필수 입니다.")
    private Long reservationId;

    @Schema(
            description = "할인 코드 (현재 주문 금액에는 적용되지 않음)",
            example = "WELCOME10",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String discountCode;

    @Schema(
            description = "배송 타입",
            example = "MOBILE",
            allowableValues = {"MOBILE", "PAPER"},
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "배송 타입은 필수 입니다.")
    @Pattern(
            regexp = "MOBILE|PAPER",
            message = "배송 타입은 MOBILE 또는 PAPER만 가능합니다."
    )
    private String deliveryType;
}
