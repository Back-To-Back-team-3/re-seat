package com.backtoback.reseat.domain.payment.pg.toss.dto.response;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;

// 토스 결제 응답 중 결제 상태 동기화에 필요한 필드만 매핑한다.
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TossPaymentResponse {

    private String paymentKey;
    private String orderId;
    private String status;
    private Integer totalAmount;
    private String approvedAt;
    private String method;

    public boolean isApproved() {
        return parsedStatus()
            .filter(TossPaymentStatus::isApproved)
            .isPresent();
    }

    public boolean isCancelCompleted() {
        return parsedStatus()
            .filter(TossPaymentStatus::isCancelCompleted)
            .isPresent();
    }

    public boolean isConfirmFailureStatus() {
        return parsedStatus()
            .filter(TossPaymentStatus::isConfirmFailureStatus)
            .isPresent();
    }

    private Optional<TossPaymentStatus> parsedStatus() {
        return TossPaymentStatus.from(status);
    }
}
