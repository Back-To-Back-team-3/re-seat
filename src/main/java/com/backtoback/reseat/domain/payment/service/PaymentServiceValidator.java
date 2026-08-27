package com.backtoback.reseat.domain.payment.service;

import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyConflictException;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyRequiredException;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyUnavailableException;
import com.backtoback.reseat.domain.payment.exception.PaymentAccessDeniedException;
import com.backtoback.reseat.domain.payment.exception.PaymentAlreadyFinalizedException;
import com.backtoback.reseat.domain.payment.exception.PaymentCallbackMismatchException;
import com.backtoback.reseat.domain.payment.exception.PaymentCancelNotAllowedException;
import com.backtoback.reseat.domain.payment.exception.PaymentPgKeyMissingException;

@Component
public class PaymentServiceValidator {

    /**
     * 결제 생성 요청에 필요한 멱등키가 비어 있지 않은지 검증한다.
     */
    public void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException();
        }
    }

    /**
     * 기존 멱등키 결제가 현재 요청의 주문과 같은지 검증한다.
     */
    public void validateIdempotencyRequest(Payment payment, Long orderId) {
        if (!payment.getOrder().getId().equals(orderId)) {
            throw new IdempotencyKeyConflictException();
        }
    }

    /**
     * 요청 멱등키가 현재 결제 시도의 활성 키인지 검증한다.
     */
    public void validateActiveIdempotencyKey(Payment payment, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        if (!payment.getIdempotencyKey().equals(idempotencyKey)) {
            throw new IdempotencyKeyUnavailableException();
        }
    }

    /**
     * 결제가 현재 사용자의 소유인지 검증한다.
     */
    public void validateOwner(Payment payment, Long userId) {
        if (!payment.getUser().getId().equals(userId)) {
            throw new PaymentAccessDeniedException();
        }
    }

    /**
     * 결제가 승인 가능한 상태이고 콜백 주문과 금액이 일치하는지 검증한다.
     */
    public void validateConfirmable(Payment payment, String pgOrderId, Integer amount) {
        if (!payment.isReady()) {
            throw new PaymentAlreadyFinalizedException();
        }
        validatePgOrderId(payment, pgOrderId);
        validateAmount(payment, amount);
    }

    /**
     * 결제가 실패 처리 가능한 READY 상태인지 검증한다.
     */
    public void validateFailable(Payment payment) {
        if (!payment.isReady()) {
            throw new PaymentAlreadyFinalizedException();
        }
    }

    /**
     * 결제가 취소 가능한 상태이고 PG 결제 키를 가지고 있는지 검증한다.
     */
    public void validateCancelable(Payment payment) {
        if (!payment.isApproved()) {
            throw new PaymentCancelNotAllowedException();
        }

        if (payment.getPgPaymentKey() == null || payment.getPgPaymentKey().isBlank()) {
            throw new PaymentPgKeyMissingException();
        }
    }

    /**
     * 로컬 결제와 PG 주문 ID가 일치하는지 검증한다.
     */
    public void validatePgOrderId(Payment payment, String pgOrderId) {
        if (!payment.getPgOrderId().equals(pgOrderId)) {
            throw new PaymentCallbackMismatchException();
        }
    }

    /**
     * 로컬 결제와 PG 결제 금액이 일치하는지 검증한다.
     */
    public void validateAmount(Payment payment, Integer amount) {
        if (!payment.getAmount().equals(amount)) {
            throw new PaymentCallbackMismatchException();
        }
    }
}
