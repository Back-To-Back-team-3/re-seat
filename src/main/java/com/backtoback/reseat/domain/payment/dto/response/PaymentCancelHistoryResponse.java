package com.backtoback.reseat.domain.payment.dto.response;

import java.time.LocalDateTime;

import com.backtoback.reseat.domain.payment.entity.PaymentCancel;
import com.backtoback.reseat.domain.payment.entity.PaymentCancelStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "티켓 단위 결제 취소 이력")
public class PaymentCancelHistoryResponse {

    @Schema(
        description = "결제 취소 이력 ID",
        example = "8801"
    )
    private final Long paymentCancelId;

    @Schema(
        description = "취소 대상 티켓 ID",
        example = "9051"
    )
    private final Long ticketId;

    @Schema(
        description = "티켓에 해당하는 취소 금액",
        example = "18000"
    )
    private final Integer cancelAmount;

    @Schema(
        description = "결제 취소 처리 상태",
        example = "DONE"
    )
    private final PaymentCancelStatus cancelStatus;

    @Schema(
        description = "PG에 전달한 결제 취소 사유",
        example = "사용자 티켓 취소"
    )
    private final String cancelReason;

    @Schema(
        description = "결제 취소 최초 요청 시각",
        example = "2026-07-10 15:00:00"
    )
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime requestedAt;

    @Schema(
        description = "결제 취소 완료 시각. 완료되지 않았다면 null",
        example = "2026-07-10 15:00:02",
        nullable = true
    )
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime completedAt;

    /** 결제 취소 이력을 조회 응답으로 변환한다. */
    public static PaymentCancelHistoryResponse from(PaymentCancel paymentCancel) {
        return PaymentCancelHistoryResponse
            .builder()
            .paymentCancelId(paymentCancel.getId())
            .ticketId(paymentCancel.getTicket().getId())
            .cancelAmount(paymentCancel.getTicket().getOrderItem().getPrice())
            .cancelStatus(paymentCancel.getStatus())
            .cancelReason(paymentCancel.getReason())
            .requestedAt(paymentCancel.getCreatedAt())
            .completedAt(paymentCancel.getCompletedAt())
            .build();
    }
}
