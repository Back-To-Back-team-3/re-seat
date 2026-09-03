package com.backtoback.reseat.domain.payment.pg.toss.entity;

import java.util.Optional;

public enum TossPaymentStatus {
    READY, IN_PROGRESS, WAITING_FOR_DEPOSIT, DONE, CANCELED, PARTIAL_CANCELED, ABORTED, EXPIRED;

    public static Optional<TossPaymentStatus> from(String value) {
        try {
            return Optional.of(TossPaymentStatus.valueOf(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    public boolean isApproved() {
        return this == DONE;
    }

    public boolean isCancelCompleted() {
        return this == CANCELED;
    }

    public boolean isConfirmFailureStatus() {
        return this == ABORTED || this == EXPIRED || this == CANCELED;
    }
}
