package com.backtoback.reseat.domain.payment.dto.response;

import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "결제 요청 응답")
public class PaymentCreateResponse {

    @Schema(description = "결제 ID", example = "1001")
    private final Long paymentId;

    @Schema(description = "서비스 내부 결제 번호", example = "PAY-20260725120000-a1b2c3d4")
    private final String paymentNo;

    @Schema(description = "주문 ID", example = "1001")
    private final Long orderId;

    @Schema(description = "결제 금액", example = "34000")
    private final Integer amount;

    @Schema(description = "결제 수단. 승인 전에는 null", example = "CARD", nullable = true)
    private final String method;

    @Schema(description = "결제 상태", example = "READY")
    private final PaymentStatus status;

    @Schema(description = "PG 제공사", example = "TOSS")
    private final PgProvider pgProvider;

    @Schema(description = "Toss에 전달할 주문 번호", example = "ORD-20260725-A1B2C3")
    private final String pgOrderId;

    public static PaymentCreateResponse from(Payment payment) {
        return PaymentCreateResponse.builder()
            .paymentId(payment.getId())
            .paymentNo(payment.getPaymentNo())
            .orderId(payment.getOrder().getId())
            .amount(payment.getAmount())
            .method(payment.getMethod())
            .status(payment.getStatus())
            .pgProvider(payment.getPgProvider())
            .pgOrderId(payment.getPgOrderId())
            .build();
    }
}
