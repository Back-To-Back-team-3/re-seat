package com.backtoback.reseat.domain.order.dto.request;

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
public class OrderCreateRequest {

    @NotNull(message = "예약 ID는 필수 입니다.")
    private Long reservationId;

    private String discountCode;

    @NotBlank(message = "배송 타입은 필수 입니다.")
    @Pattern(
            regexp = "MOBILE|PAPER",
            message = "배송 타입은 MOBILE 또는 PAPER만 가능합니다."
    )
    private String deliveryType;
}
