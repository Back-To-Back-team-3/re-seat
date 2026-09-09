package com.backtoback.reseat.domain.payment.schedule;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryType;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;
import com.backtoback.reseat.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalCompensationRecoveryHandler 승인 보상 복구")
class ApprovalCompensationRecoveryHandlerTest {

    private static final Long PAYMENT_ID = 1L;
    private static final Long ORDER_ID = 2L;
    private static final String PAYMENT_KEY = "payment-key";
    private static final String CANCEL_REASON = "승인 후 로컬 반영 실패 결제 자동 환불";

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private ApprovalCompensationRecoveryHandler handler;

    private PaymentRecoveryTask compensationTask(OrderStatus orderStatus) {
        Order order = mock(Order.class);
        lenient().when(order.getId()).thenReturn(ORDER_ID);
        lenient().when(order.getStatus()).thenReturn(orderStatus);

        Payment payment
            = Payment
                .builder()
                .paymentNo("PAY-20260909000000-000001")
                .order(order)
                .user(mock(User.class))
                .amount(10000)
                .idempotencyKey("idempotency-key")
                .status(PaymentStatus.FAILED)
                .pgProvider(PgProvider.TOSS)
                .pgOrderId("order-no")
                .build();
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.assignPgPaymentKey(PAYMENT_KEY);
        return PaymentRecoveryTask.createApprovalCompensation(payment);
    }

    @Nested
    @DisplayName("승인 후 로컬 반영 실패를 보상한다")
    class Recover {

        @Test
        @DisplayName("승인된 PG 결제를 취소하고 생성 상태의 주문을 실패 처리한다.")
        void cancelsApprovedPaymentAndFailsOrder() {
            PaymentRecoveryTask task = compensationTask(OrderStatus.CREATED);
            TossPaymentResponse paymentResponse = mock(TossPaymentResponse.class);
            TossPaymentResponse cancelResponse = mock(TossPaymentResponse.class);
            when(paymentResponse.isApproved()).thenReturn(true);
            when(cancelResponse.isCancelCompleted()).thenReturn(true);
            when(tossPaymentClient.getPayment(PAYMENT_KEY)).thenReturn(paymentResponse);
            when(tossPaymentClient.cancel(PAYMENT_KEY, CANCEL_REASON)).thenReturn(cancelResponse);

            PaymentRecoveryResult result = handler.recover(task);

            assertThat(result.successful()).isTrue();
            verify(tossPaymentClient).cancel(PAYMENT_KEY, CANCEL_REASON);
            verify(orderService).failOrder(ORDER_ID);
        }

        @Test
        @DisplayName("PG 취소와 주문 종결이 이미 완료됐다면 외부 요청과 상태 전이를 반복하지 않는다.")
        void completesAlreadyReconciledTask() {
            PaymentRecoveryTask task = compensationTask(OrderStatus.CANCELED);
            TossPaymentResponse paymentResponse = mock(TossPaymentResponse.class);
            when(paymentResponse.isCancelCompleted()).thenReturn(true);
            when(tossPaymentClient.getPayment(PAYMENT_KEY)).thenReturn(paymentResponse);

            PaymentRecoveryResult result = handler.recover(task);

            assertThat(result.successful()).isTrue();
            verify(tossPaymentClient, never()).cancel(anyString(), anyString());
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("PG 취소 완료를 확인할 수 없으면 주문을 변경하지 않고 재시도를 요청한다.")
        void retriesWhenCancelIsNotCompleted() {
            PaymentRecoveryTask task = compensationTask(OrderStatus.CREATED);
            TossPaymentResponse paymentResponse = mock(TossPaymentResponse.class);
            TossPaymentResponse cancelResponse = mock(TossPaymentResponse.class);
            when(paymentResponse.isApproved()).thenReturn(true);
            when(tossPaymentClient.getPayment(PAYMENT_KEY)).thenReturn(paymentResponse);
            when(tossPaymentClient.cancel(PAYMENT_KEY, CANCEL_REASON)).thenReturn(cancelResponse);

            PaymentRecoveryResult result = handler.recover(task);

            assertThat(result.successful()).isFalse();
            assertThat(result.retryable()).isTrue();
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("PG 취소 후 주문 전이에 실패하면 예외를 전파해 전체 복구 처리를 재시도한다.")
        void propagatesOrderTransitionFailure() {
            PaymentRecoveryTask task = compensationTask(OrderStatus.CREATED);
            TossPaymentResponse paymentResponse = mock(TossPaymentResponse.class);
            TossPaymentResponse cancelResponse = mock(TossPaymentResponse.class);
            RuntimeException failure = new RuntimeException("주문 상태 전이 실패");
            when(paymentResponse.isApproved()).thenReturn(true);
            when(cancelResponse.isCancelCompleted()).thenReturn(true);
            when(tossPaymentClient.getPayment(PAYMENT_KEY)).thenReturn(paymentResponse);
            when(tossPaymentClient.cancel(PAYMENT_KEY, CANCEL_REASON)).thenReturn(cancelResponse);
            doThrow(failure).when(orderService).failOrder(ORDER_ID);

            assertThatThrownBy(() -> handler.recover(task)).isSameAs(failure);
        }
    }

    @Test
    @DisplayName("승인 보상 복구 유형을 지원한다.")
    void supportsApprovalCompensationType() {
        assertThat(handler.getType()).isEqualTo(PaymentRecoveryType.APPROVAL_COMPENSATION);
    }
}
