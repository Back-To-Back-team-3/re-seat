package com.backtoback.reseat.domain.payment.schedule;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentCancel;
import com.backtoback.reseat.domain.payment.entity.PaymentCancelStatus;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossCancelResponse;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;
import com.backtoback.reseat.domain.payment.pg.toss.exception.TossApiException;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.service.TicketService;
import com.backtoback.reseat.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartialCancelRecoveryHandler 부분 취소 복구")
class PartialCancelRecoveryHandlerTest {

    private static final Long ORDER_ITEM_ID = 10L;
    private static final Long TICKET_ID = 20L;
    private static final Long PAYMENT_ID = 100L;
    private static final Long PAYMENT_CANCEL_ID = 200L;
    private static final int PAYMENT_AMOUNT = 10000;
    private static final String PAYMENT_KEY = "payment-key";
    private static final String PG_IDEMPOTENCY_KEY = "pg-idempotency-key";

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private OrderService orderService;

    @Mock
    private TicketService ticketService;

    @InjectMocks
    private PartialCancelRecoveryHandler handler;

    private PaymentRecoveryTask partialCancelTask(int cancelAmount) {
        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);
        Ticket ticket = mock(Ticket.class);
        lenient().when(orderItem.getId()).thenReturn(ORDER_ITEM_ID);
        lenient().when(orderItem.getPrice()).thenReturn(cancelAmount);
        lenient().when(ticket.getId()).thenReturn(TICKET_ID);
        lenient().when(ticket.getOrderItem()).thenReturn(orderItem);

        Payment payment
            = Payment
                .builder()
                .paymentNo("PAY-20260902000000-000001")
                .order(order)
                .user(mock(User.class))
                .amount(PAYMENT_AMOUNT)
                .idempotencyKey("payment-idempotency-key")
                .status(PaymentStatus.APPROVED)
                .pgProvider(PgProvider.TOSS)
                .pgOrderId("order-no")
                .build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.assignPgPaymentKey(PAYMENT_KEY);

        PaymentCancel paymentCancel = PaymentCancel.create(payment, ticket, "사용자 티켓 취소", PG_IDEMPOTENCY_KEY);
        ReflectionTestUtils.setField(paymentCancel, "id", PAYMENT_CANCEL_ID);
        return PaymentRecoveryTask.createPartialCancel(paymentCancel);
    }

    private TossPaymentResponse completedResponse(int cancelAmount) {
        TossPaymentResponse response = mock(TossPaymentResponse.class);
        TossCancelResponse cancel = mock(TossCancelResponse.class);
        when(response.getCancels()).thenReturn(List.of(cancel));
        when(response.getLastTransactionKey()).thenReturn("transaction-key");
        when(cancel.isDone()).thenReturn(true);
        when(cancel.getCancelAmount()).thenReturn(cancelAmount);
        when(cancel.getTransactionKey()).thenReturn("transaction-key");
        when(cancel.getCanceledAt()).thenReturn("2026-09-02T12:00:00+09:00");
        return response;
    }

    @Nested
    @DisplayName("저장된 부분 취소 작업을 복구한다")
    class Recover {

        @Test
        @DisplayName("이미 완료된 취소 이력은 PG를 다시 호출하지 않고 티켓 환불을 완료한다.")
        void completesTicketForCompletedCancel() {
            PaymentRecoveryTask task = partialCancelTask(4000);
            task.getPaymentCancel().complete("transaction-key", LocalDateTime.of(2026, 9, 2, 12, 0));

            PaymentRecoveryResult result = handler.recover(task);

            assertThat(result.successful()).isTrue();
            verify(ticketService).completeTicketRefund(TICKET_ID);
            verifyNoInteractions(tossPaymentClient, orderService);
        }

        @Test
        @DisplayName("일부 금액 취소가 완료되면 취소 이력과 결제를 부분 취소 상태로 전환한다.")
        void completesPartialCancel() {
            int cancelAmount = 4000;
            PaymentRecoveryTask task = partialCancelTask(cancelAmount);
            TossPaymentResponse response = completedResponse(cancelAmount);
            when(tossPaymentClient.cancel(PAYMENT_KEY, "사용자 티켓 취소", cancelAmount, PG_IDEMPOTENCY_KEY))
                .thenReturn(response);

            PaymentRecoveryResult result = handler.recover(task);

            assertThat(result.successful()).isTrue();
            assertThat(task.getPaymentCancel().getStatus()).isEqualTo(PaymentCancelStatus.DONE);
            assertThat(task.getPaymentCancel().getCompletedAt()).isEqualTo(LocalDateTime.of(2026, 9, 2, 12, 0));
            assertThat(task.getPayment().getStatus()).isEqualTo(PaymentStatus.PARTIALLY_CANCELED);
            assertThat(task.getPayment().getRemainingAmount()).isEqualTo(6000);
            verify(orderService).refundOrder(ORDER_ITEM_ID);
            verify(ticketService).completeTicketRefund(TICKET_ID);
        }

        @Test
        @DisplayName("결제 잔액을 모두 취소하면 주문과 결제를 전체 취소 상태로 전환한다.")
        void completesFullCancelWhenNoAmountRemains() {
            PaymentRecoveryTask task = partialCancelTask(PAYMENT_AMOUNT);
            TossPaymentResponse response = completedResponse(PAYMENT_AMOUNT);
            when(tossPaymentClient.cancel(PAYMENT_KEY, "사용자 티켓 취소", PAYMENT_AMOUNT, PG_IDEMPOTENCY_KEY))
                .thenReturn(response);

            PaymentRecoveryResult result = handler.recover(task);

            assertThat(result.successful()).isTrue();
            assertThat(task.getPayment().getStatus()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(task.getPayment().getRemainingAmount()).isZero();
            verify(orderService).refundOrder(ORDER_ITEM_ID);
        }

        @Test
        @DisplayName("취소 목록 순서와 관계없이 마지막 거래 키와 일치하는 취소 결과를 반영한다.")
        void selectsCancelByLastTransactionKey() {
            int cancelAmount = 4000;
            PaymentRecoveryTask task = partialCancelTask(cancelAmount);
            TossPaymentResponse response = mock(TossPaymentResponse.class);
            TossCancelResponse completedCancel = mock(TossCancelResponse.class);
            TossCancelResponse anotherCancel = mock(TossCancelResponse.class);
            when(response.getLastTransactionKey()).thenReturn("completed-transaction-key");
            when(response.getCancels()).thenReturn(List.of(completedCancel, anotherCancel));
            when(completedCancel.getTransactionKey()).thenReturn("completed-transaction-key");
            when(completedCancel.isDone()).thenReturn(true);
            when(completedCancel.getCancelAmount()).thenReturn(cancelAmount);
            when(completedCancel.getCanceledAt()).thenReturn("2026-09-02T12:00:00+09:00");
            when(tossPaymentClient.cancel(PAYMENT_KEY, "사용자 티켓 취소", cancelAmount, PG_IDEMPOTENCY_KEY))
                .thenReturn(response);

            PaymentRecoveryResult result = handler.recover(task);

            assertThat(result.successful()).isTrue();
            assertThat(task.getPaymentCancel().getPgTransactionKey()).isEqualTo("completed-transaction-key");
        }

        @Test
        @DisplayName("Toss 4xx 응답은 취소 이력을 실패 처리하고 재시도하지 않는다.")
        void failsPermanentlyForClientError() {
            PaymentRecoveryTask task = partialCancelTask(4000);
            when(tossPaymentClient.cancel(PAYMENT_KEY, "사용자 티켓 취소", 4000, PG_IDEMPOTENCY_KEY))
                .thenThrow(new TossApiException("취소", 400, "INVALID_REQUEST"));

            PaymentRecoveryResult result = handler.recover(task);

            assertThat(result.successful()).isFalse();
            assertThat(result.retryable()).isFalse();
            assertThat(task.getPaymentCancel().getStatus()).isEqualTo(PaymentCancelStatus.FAILED);
            assertThat(task.getPayment().getStatus()).isEqualTo(PaymentStatus.APPROVED);
            verify(ticketService).failTicketRefund(TICKET_ID);
        }

        @Test
        @DisplayName("Toss 5xx 응답은 취소 이력과 PG 멱등키를 유지하고 재시도를 요청한다.")
        void retriesForServerError() {
            PaymentRecoveryTask task = partialCancelTask(4000);
            when(tossPaymentClient.cancel(PAYMENT_KEY, "사용자 티켓 취소", 4000, PG_IDEMPOTENCY_KEY))
                .thenThrow(new TossApiException("취소", 500, "INTERNAL_ERROR"));

            PaymentRecoveryResult result = handler.recover(task);

            assertThat(result.successful()).isFalse();
            assertThat(result.retryable()).isTrue();
            assertThat(task.getPaymentCancel().getStatus()).isEqualTo(PaymentCancelStatus.PENDING);
            assertThat(task.getPaymentCancel().getPgIdempotencyKey()).isEqualTo(PG_IDEMPOTENCY_KEY);
            assertThat(task.getPayment().getStatus()).isEqualTo(PaymentStatus.APPROVED);
            verifyNoInteractions(ticketService);
        }
    }

    @Nested
    @DisplayName("재시도 횟수를 모두 소진한 부분 취소를 최종 실패 처리한다")
    class HandleFinalFailure {

        @Test
        @DisplayName("취소 이력과 티켓을 실패 상태로 전환한다.")
        void failsPaymentCancelAndTicket() {
            PaymentRecoveryTask task = partialCancelTask(4000);
            LocalDateTime failedAt = LocalDateTime.of(2026, 9, 3, 12, 0);

            handler.handleFinalFailure(task, "최대 재시도 횟수 초과", failedAt);

            assertThat(task.getPaymentCancel().getStatus()).isEqualTo(PaymentCancelStatus.FAILED);
            assertThat(task.getPaymentCancel().getFailureReason()).isEqualTo("최대 재시도 횟수 초과");
            assertThat(task.getPaymentCancel().getFailedAt()).isEqualTo(failedAt);
            verify(ticketService).failTicketRefund(TICKET_ID);
        }
    }
}
