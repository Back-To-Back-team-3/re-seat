package com.backtoback.reseat.domain.payment.dto.response;

import java.util.List;

import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.ticket.dto.response.TicketListResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "결제 승인 처리 응답")
public class PaymentCompleteResponse {
    @Schema(
        description = "결제 ID",
        example = "1001"
    )
    private final Long paymentId;

    @Schema(
        description = "서비스 내부 결제 번호",
        example = "PAY-20260725120000-a1b2c3d4"
    )
    private final String paymentNo;

    @Schema(
        description = "처리 후 결제 상태",
        example = "APPROVED"
    )
    private final PaymentStatus status;

    @Schema(
        description = "PG 승인 결과에 포함된 결제 수단. 승인 실패 시에는 null",
        example = "간편결제",
        nullable = true
    )
    private final String method;

    @Schema(
        description = "주문 ID",
        example = "7001"
    )
    private final Long orderId;

    @Schema(
        description = "처리 후 주문 상태",
        example = "PAID"
    )
    private final OrderStatus orderStatus;

    @Schema(
        description = "티켓 목록",
        example = """
            [
                {
                    "ticketId": 1234,
                    "ticketNo": "TKT-20260812-A1B2C3",
                    "gameId": 100,
                    "seat": "1루 A-3-12",
                    "status": "ISSUED",
                    "qrToken": "550e8400-e29b-41d4-a716-446655440000",
                    "gameAt": "2026-08-20T18:30:00"
                }
            ]
            """
    )
    private final List<TicketListResponse> tickets;

    public static PaymentCompleteResponse from(Payment payment, List<TicketListResponse> tickets) {
        return PaymentCompleteResponse
            .builder()
            .paymentId(payment.getId())
            .paymentNo(payment.getPaymentNo())
            .status(payment.getStatus())
            .method(payment.getMethod())
            .orderId(payment.getOrder().getId())
            .orderStatus(payment.getOrder().getStatus())
            .tickets(List.copyOf(tickets))
            .build();
    }
}
