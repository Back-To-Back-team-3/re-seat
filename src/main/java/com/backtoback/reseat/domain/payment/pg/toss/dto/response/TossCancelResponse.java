package com.backtoback.reseat.domain.payment.pg.toss.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** Toss가 반환한 개별 결제 취소 거래를 나타낸다. */
@Getter
@NoArgsConstructor
public class TossCancelResponse {

    /** 취소 금액. */
    private Integer cancelAmount;

    /** 취소 사유. */
    private String cancelReason;

    /** 취소 후 환불 가능한 잔액. */
    private Integer refundableAmount;

    /** 취소 완료 시각. */
    private String canceledAt;

    /** 취소 거래 식별자. */
    private String transactionKey;

    /** 취소 처리 상태. */
    private String cancelStatus;

    /** 취소 거래가 완료 상태인지 확인한다. */
    public boolean isDone() {
        return "DONE".equals(cancelStatus);
    }
}
