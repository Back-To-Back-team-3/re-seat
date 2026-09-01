package com.backtoback.reseat.domain.payment.schedule;

/** 유형별 결제 복구 처리 결과를 공통 상태 전이 정보로 변환한다. */
public record PaymentRecoveryResult(boolean successful, String error) {

    /** 복구 완료 결과를 생성한다. */
    public static PaymentRecoveryResult success() {
        return new PaymentRecoveryResult(true, null);
    }

    /** 재시도가 필요한 복구 결과를 생성한다. */
    public static PaymentRecoveryResult retry(String error) {
        if (error == null || error.isBlank()) {
            throw new IllegalArgumentException("복구 재시도 사유는 필수입니다.");
        }
        return new PaymentRecoveryResult(false, error);
    }
}
