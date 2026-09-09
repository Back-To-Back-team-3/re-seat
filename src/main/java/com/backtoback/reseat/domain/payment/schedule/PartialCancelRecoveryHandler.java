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
import com.backtoback.reseat.domain.payment.pg.toss.exception.TossApiException;
import com.backtoback.reseat.domain.ticket.service.TicketService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartialCancelRecoveryHandler implements PaymentRecoveryHandler {

    private final TossPaymentClient tossPaymentClient;
    private final OrderService orderService;
    private final TicketService ticketService;

    /**
     * 저장된 취소 시도를 Toss에 요청하고 완료된 부분 취소를 로컬 상태에 반영한다.
     */
    @Override
    public PaymentRecoveryResult recover(PaymentRecoveryTask task) {
        PaymentCancel paymentCancel = task.getPaymentCancel();
        if (paymentCancel == null) {
            return PaymentRecoveryResult.failure("부분 취소 복구 대상이 없습니다.");
        }

        Long orderItemId = paymentCancel.getTicket().getOrderItem().getId();

        if (paymentCancel.isDone()) {
            // PG 취소는 이미 완료됐으나 로컬 반영이 중단된 재시도 경로.
            // 정상 경로와 동일하게 티켓 확정 → 좌석 반환 순서를 지킨다.
            ticketService.completeTicketRefund(paymentCancel.getTicket().getId());
            orderService.refundOrder(orderItemId);
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
        } catch (TossApiException exception) {
            if (isClientError(exception)) {
                failPartialCancel(paymentCancel, exception.getMessage(), LocalDateTime.now());
                return PaymentRecoveryResult.failure("Toss가 부분 취소 요청을 거절했습니다.");
            }
            return retryAfterFailure(task, payment, paymentCancel, exception);
        } catch (RuntimeException exception) {
            return retryAfterFailure(task, payment, paymentCancel, exception);
        }

        LocalDateTime canceledAt = resolveCanceledAt(completedCancel.getCanceledAt());

        // 좌석 반환보다 먼저 결제·취소 이력·티켓 순으로 환불 확정을 마친다.
        // uk_tickets_active_seat가 REFUNDED만 NULL로 빼주므로,
        // 이 순서를 지켜야 재판매 시점에 활성 티켓 2건이 동시에 존재하는 창이 생기지 않는다.
        updatePaymentStatus(payment, cancelAmount);
        paymentCancel.complete(completedCancel.getTransactionKey(), canceledAt);
        ticketService.completeTicketRefund(paymentCancel.getTicket().getId());

        // 티켓이 REFUNDED로 확정된 뒤에야 좌석을 반환한다.
        orderService.refundOrder(orderItemId);

        return PaymentRecoveryResult.success();
    }

    /**
     * 재시도 횟수를 모두 소진한 부분 취소 이력과 티켓을 최종 실패로 전이한다.
     */
    @Override
    public void handleFinalFailure(PaymentRecoveryTask task, String error, LocalDateTime failedAt) {
        PaymentCancel paymentCancel = task.getPaymentCancel();
        if (paymentCancel == null) {
            return;
        }
        if (!paymentCancel.isFailed()) {
            paymentCancel.fail(error, failedAt);
        }
        ticketService.failTicketRefund(paymentCancel.getTicket().getId());
    }

    private void failPartialCancel(PaymentCancel paymentCancel, String error, LocalDateTime failedAt) {
        paymentCancel.fail(error, failedAt);
        ticketService.failTicketRefund(paymentCancel.getTicket().getId());
    }

    /**
     * 일시적인 PG 요청 실패를 기록하고 동일 멱등키 재시도를 요청한다.
     */
    private PaymentRecoveryResult retryAfterFailure(
        PaymentRecoveryTask task,
        Payment payment,
        PaymentCancel paymentCancel,
        RuntimeException exception
    ) {
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

    /**
     * Toss가 요청 자체를 거절한 4xx 응답인지 확인한다.
     */
    private boolean isClientError(TossApiException exception) {
        return exception.getStatusCode() >= 400 && exception.getStatusCode() < 500;
    }

    /**
     * Toss의 마지막 거래 키와 일치하는 취소 거래가 정상 완료되었는지 확인한다.
     */
    private TossCancelResponse findCompletedCancel(TossPaymentResponse response, int cancelAmount) {
        List<TossCancelResponse> cancels = response.getCancels();
        String lastTransactionKey = response.getLastTransactionKey();
        if (cancels == null || cancels.isEmpty() || isBlank(lastTransactionKey)) {
            return null;
        }

        TossCancelResponse cancel
            = cancels
                .stream()
                .filter(candidate -> candidate != null && lastTransactionKey.equals(candidate.getTransactionKey()))
                .findFirst()
                .orElse(null);
        if (cancel == null || !cancel.isDone() || !Integer.valueOf(cancelAmount).equals(cancel.getCancelAmount())
            || isBlank(cancel.getCanceledAt())) {
            return null;
        }
        return cancel;
    }

    /**
     * 누적 취소 후 잔액에 따라 결제를 부분 또는 전체 취소 상태로 전환한다.
     */
    private void updatePaymentStatus(Payment payment, int cancelAmount) {
        if (payment.getRemainingAmount() - cancelAmount > 0) {
            payment.partiallyCancel();
            return;
        }

        payment.cancel();
    }

    /**
     * Toss가 반환한 ISO-8601 취소 완료 시각을 로컬 날짜시간으로 변환한다.
     */
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
