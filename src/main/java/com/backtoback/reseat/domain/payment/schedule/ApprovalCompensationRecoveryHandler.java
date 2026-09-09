package com.backtoback.reseat.domain.payment.schedule;

import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryType;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** PG 승인 후 로컬 반영에 실패한 결제를 환불하고 주문 상태를 복구한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalCompensationRecoveryHandler implements PaymentRecoveryHandler {

    private static final String RECOVERY_CANCEL_REASON = "승인 후 로컬 반영 실패 결제 자동 환불";

    private final TossPaymentClient tossPaymentClient;
    private final OrderService orderService;

    /** PG 승인 취소를 확인한 뒤 주문을 결제 실패 상태에 맞게 종결한다. */
    @Override
    public PaymentRecoveryResult recover(PaymentRecoveryTask task) {
        PaymentRecoveryResult pgResult = cancelApprovedPayment(task);
        if (!pgResult.successful()) {
            return pgResult;
        }

        return reconcileOrder(task.getPayment().getOrder());
    }

    /** Toss 결제 상태를 다시 조회하고 승인 상태라면 전액 취소한다. */
    private PaymentRecoveryResult cancelApprovedPayment(PaymentRecoveryTask task) {
        Payment payment = task.getPayment();
        String paymentKey = payment.getPgPaymentKey();

        try {
            TossPaymentResponse paymentResponse = tossPaymentClient.getPayment(paymentKey);
            if (paymentResponse.isCancelCompleted()) {
                return PaymentRecoveryResult.success();
            }
            if (!paymentResponse.isApproved()) {
                return PaymentRecoveryResult.retry("Toss 승인 결제 상태를 확인할 수 없습니다.");
            }

            TossPaymentResponse cancelResponse = tossPaymentClient.cancel(paymentKey, RECOVERY_CANCEL_REASON);
            if (!cancelResponse.isCancelCompleted()) {
                return PaymentRecoveryResult.retry("Toss 승인 결제의 자동 환불 상태를 확인할 수 없습니다.");
            }
            return PaymentRecoveryResult.success();
        } catch (RuntimeException exception) {
            log.warn("승인 보상 복구 PG 요청 실패 (taskId={}, paymentId={})", task.getId(), payment.getId(), exception);
            return PaymentRecoveryResult.retry("Toss 결제 조회 또는 자동 환불 요청에 실패했습니다.");
        }
    }

    /** 주문을 실패 처리하거나 이미 종결된 상태인지 확인한다. */
    private PaymentRecoveryResult reconcileOrder(Order order) {
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.CREATED) {
            orderService.failOrder(order.getId());
            return PaymentRecoveryResult.success();
        }
        if (status == OrderStatus.CANCELED || status == OrderStatus.EXPIRED) {
            return PaymentRecoveryResult.success();
        }

        return PaymentRecoveryResult.retry("결제 실패에 맞게 주문 상태를 종결할 수 없습니다.");
    }

    @Override
    public PaymentRecoveryType getType() {
        return PaymentRecoveryType.APPROVAL_COMPENSATION;
    }
}
