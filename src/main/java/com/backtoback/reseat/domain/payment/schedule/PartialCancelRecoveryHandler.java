package com.backtoback.reseat.domain.payment.schedule;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentCancel;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryType;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossCancelResponse;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartialCancelRecoveryHandler implements PaymentRecoveryHandler {

    private final TossPaymentClient tossPaymentClient;
    private final OrderService orderService;

    /** 저장된 취소 시도를 Toss에 요청하고 완료된 부분 취소를 로컬 상태에 반영한다. */
    @Override
    public PaymentRecoveryResult recover(PaymentRecoveryTask task) {
        PaymentCancel paymentCancel = task.getPaymentCancel();
        if (paymentCancel == null) {
            return PaymentRecoveryResult.retry("부분 취소 복구 대상이 없습니다.");
        }
        if (paymentCancel.isDone()) {
            return PaymentRecoveryResult.success();
        }

        Payment payment = task.getPayment();
        int cancelAmount = paymentCancel.getTicket().getOrderItem().getPrice();

        TossCancelResponse completedCancel;
        try {
            TossPaymentResponse response
                = tossPaymentClient
                    .cancel(
                        payment.getPgPaymentKey(),
                        paymentCancel.getReason(),
                        cancelAmount,
                        paymentCancel.getPgIdempotencyKey()
                    );
            completedCancel = findCompletedCancel(response, cancelAmount);
            if (completedCancel == null) {
                return PaymentRecoveryResult.retry("Toss 부분 취소 완료 상태를 확인할 수 없습니다.");
            }
        } catch (RuntimeException exception) {
            log
                .warn(
                    "결제 부분 취소 복구 PG 요청 실패 (taskId={}, paymentId={}, paymentCancelId={})",
                    task.getId(),
                    payment.getId(),
                    paymentCancel.getId(),
                    exception
                );
            return PaymentRecoveryResult.retry("Toss 부분 취소 요청 또는 응답 확인에 실패했습니다.");
        }

        LocalDateTime canceledAt = resolveCanceledAt(completedCancel.getCanceledAt());
        updatePaymentStatus(payment, cancelAmount);
        paymentCancel.complete(completedCancel.getTransactionKey(), canceledAt);
        return PaymentRecoveryResult.success();
    }

    /** Toss 응답의 마지막 취소 거래가 이번 부분 취소 요청과 일치하는지 확인한다. */
    private TossCancelResponse findCompletedCancel(TossPaymentResponse response, int cancelAmount) {
        List<TossCancelResponse> cancels = response.getCancels();
        if (cancels == null || cancels.isEmpty()) {
            return null;
        }

        TossCancelResponse cancel = cancels.get(cancels.size() - 1);
        if (cancel == null || !cancel.isDone() || !Integer.valueOf(cancelAmount).equals(cancel.getCancelAmount())
            || isBlank(cancel.getTransactionKey()) || isBlank(cancel.getCanceledAt())) {
            return null;
        }
        return cancel;
    }

    /** 누적 취소 후 잔액에 따라 결제를 부분 또는 전체 취소 상태로 전환한다. */
    private void updatePaymentStatus(Payment payment, int cancelAmount) {
        if (payment.getRemainingAmount() - cancelAmount > 0) {
            payment.partiallyCancel();
            return;
        }

        // 주문 상태 전이가 실패하면 결제 취소 이력도 완료되지 않도록 로컬 상태 변경보다 먼저 호출한다.
        orderService.cancelPaidOrder(payment.getOrder().getId());
        payment.cancel();
    }

    /** Toss가 반환한 ISO-8601 취소 완료 시각을 로컬 날짜시간으로 변환한다. */
    private LocalDateTime resolveCanceledAt(String canceledAt) {
        return OffsetDateTime.parse(canceledAt).toLocalDateTime();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public PaymentRecoveryType getType() {
        return PaymentRecoveryType.PARTIAL_CANCEL;
    }
}
