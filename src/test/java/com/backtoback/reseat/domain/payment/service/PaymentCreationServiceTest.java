package com.backtoback.reseat.domain.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.payment.dto.request.PaymentRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCreateResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyConflictException;
import com.backtoback.reseat.domain.payment.exception.PaymentOrderNotPayableException;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCreationService 결제 생성")
class PaymentCreationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final String NEW_IDEMPOTENCY_KEY = "new-idempotency-key";
    private static final String ORDER_NO = "ORD-20260727-000001";
    private static final int AMOUNT = 10000;
    private static final LocalDateTime PAYMENT_DEADLINE = LocalDateTime.of(2026, 7, 27, 1, 8);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentServiceValidator paymentValidator;

    @Mock
    private PaymentOrderPolicy paymentOrderPolicy;

    @InjectMocks
    private PaymentCreationService paymentCreationService;

    private PaymentRequest request() {
        PaymentRequest request = mock(PaymentRequest.class);
        when(request.getOrderId()).thenReturn(ORDER_ID);
        return request;
    }

    private Order order() {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(ORDER_ID);
        return order;
    }

    private Payment payment(Order order, PaymentStatus status, String idempotencyKey) {
        return Payment
            .builder()
            .paymentNo("PAY-20260727010000-000001")
            .order(order)
            .user(mock(User.class))
            .amount(AMOUNT)
            .idempotencyKey(idempotencyKey)
            .status(status)
            .pgProvider(PgProvider.TOSS)
            .pgOrderId(ORDER_NO)
            .build();
    }

    @Nested
    @DisplayName("주문 기준 결제를 요청한다")
    class RequestPayment {

        @Test
        @DisplayName("동일 멱등키의 READY 결제는 주문을 검증한 뒤 기존 결과를 반환한다.")
        void returnsExistingReadyPaymentForSameKey() {
            PaymentRequest request = request();
            Order order = order();
            Payment payment = payment(order, PaymentStatus.READY, IDEMPOTENCY_KEY);
            when(order.getPaymentDeadline()).thenReturn(PAYMENT_DEADLINE);
            when(paymentRepository.findByIdempotencyKeyWithPessimisticWriteLock(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(payment));

            PaymentCreateResponse response = paymentCreationService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.READY);
            assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(response.getPaymentDeadline()).isEqualTo(PAYMENT_DEADLINE);
            verify(paymentValidator).validateOwner(payment, USER_ID);
            verify(paymentValidator).validateIdempotencyRequest(payment, ORDER_ID);
            verify(paymentOrderPolicy).ensurePayable(payment, order);
            verify(paymentRepository, never()).findByOrderIdWithPessimisticWriteLock(any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("동일 멱등키의 종결 결제는 주문 결제 가능 여부를 다시 검증하지 않고 기존 결과를 반환한다.")
        void returnsFinalizedPaymentForSameKey() {
            PaymentRequest request = request();
            Payment payment = payment(order(), PaymentStatus.APPROVED, IDEMPOTENCY_KEY);
            when(paymentRepository.findByIdempotencyKeyWithPessimisticWriteLock(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(payment));

            PaymentCreateResponse response = paymentCreationService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            verify(paymentValidator).validateOwner(payment, USER_ID);
            verify(paymentValidator).validateIdempotencyRequest(payment, ORDER_ID);
            verifyNoInteractions(paymentOrderPolicy);
        }

        @Test
        @DisplayName("동일 멱등키로 다른 주문을 요청하면 충돌 예외를 전파한다.")
        void rejectsSameKeyForDifferentOrder() {
            PaymentRequest request = request();
            Payment payment = payment(mock(Order.class), PaymentStatus.READY, IDEMPOTENCY_KEY);
            when(paymentRepository.findByIdempotencyKeyWithPessimisticWriteLock(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(payment));
            doThrow(new IdempotencyKeyConflictException())
                .when(paymentValidator)
                .validateIdempotencyRequest(payment, ORDER_ID);

            assertThatThrownBy(() -> paymentCreationService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request))
                .isInstanceOf(IdempotencyKeyConflictException.class);

            verify(paymentOrderPolicy, never()).ensurePayable(any(), any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("새 멱등키로 기존 READY 결제를 요청하면 활성 키를 교체해 반환한다.")
        void replacesIdempotencyKeyOfExistingReadyPayment() {
            PaymentRequest request = request();
            Order order = order();
            Payment payment = payment(order, PaymentStatus.READY, IDEMPOTENCY_KEY);
            when(paymentRepository.findByIdempotencyKeyWithPessimisticWriteLock(NEW_IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
            when(paymentOrderPolicy.getOwnedOrder(USER_ID, ORDER_ID)).thenReturn(order);
            when(paymentRepository.findByOrderIdWithPessimisticWriteLock(ORDER_ID)).thenReturn(Optional.of(payment));

            PaymentCreateResponse response
                = paymentCreationService.requestPayment(USER_ID, NEW_IDEMPOTENCY_KEY, request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.READY);
            assertThat(payment.getIdempotencyKey()).isEqualTo(NEW_IDEMPOTENCY_KEY);
            verify(paymentOrderPolicy).ensurePayable(payment, order);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("새 멱등키로 요청해도 기존 APPROVED 결제가 있으면 승인 결과를 반환한다.")
        void returnsExistingApprovedPaymentForNewKey() {
            PaymentRequest request = request();
            Order order = order();
            Payment payment = payment(order, PaymentStatus.APPROVED, IDEMPOTENCY_KEY);
            when(paymentRepository.findByIdempotencyKeyWithPessimisticWriteLock(NEW_IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
            when(paymentOrderPolicy.getOwnedOrder(USER_ID, ORDER_ID)).thenReturn(order);
            when(paymentRepository.findByOrderIdWithPessimisticWriteLock(ORDER_ID)).thenReturn(Optional.of(payment));

            PaymentCreateResponse response
                = paymentCreationService.requestPayment(USER_ID, NEW_IDEMPOTENCY_KEY, request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
            verify(paymentOrderPolicy, never()).ensurePayable(any(), any());
            verify(paymentRepository, never()).save(any());
        }

        @ParameterizedTest(name = "{0} 결제가 있는 주문에는 새 결제를 생성할 수 없다")
        @EnumSource(
            value = PaymentStatus.class,
            names = {
                "FAILED",
                "CANCELED"
            }
        )
        @DisplayName("기존 결제가 실패 또는 취소 상태면 결제 불가 예외가 발생한다.")
        void rejectsOrderWithUnavailablePayment(PaymentStatus status) {
            PaymentRequest request = request();
            Order order = order();
            Payment payment = payment(order, status, IDEMPOTENCY_KEY);
            when(paymentRepository.findByIdempotencyKeyWithPessimisticWriteLock(NEW_IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
            when(paymentOrderPolicy.getOwnedOrder(USER_ID, ORDER_ID)).thenReturn(order);
            when(paymentRepository.findByOrderIdWithPessimisticWriteLock(ORDER_ID)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentCreationService.requestPayment(USER_ID, NEW_IDEMPOTENCY_KEY, request))
                .isInstanceOf(PaymentOrderNotPayableException.class);

            verify(paymentOrderPolicy, never()).ensurePayable(any(), any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("기존 결제가 없으면 주문 정보로 새로운 READY 결제를 생성한다.")
        void createsReadyPaymentWhenPaymentDoesNotExist() {
            PaymentRequest request = request();
            Order order = order();
            User user = mock(User.class);
            when(order.getUser()).thenReturn(user);
            when(order.getTotalAmount()).thenReturn(AMOUNT);
            when(order.getOrderNo()).thenReturn(ORDER_NO);
            when(order.getPaymentDeadline()).thenReturn(PAYMENT_DEADLINE);
            when(paymentRepository.findByIdempotencyKeyWithPessimisticWriteLock(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
            when(paymentOrderPolicy.getOwnedOrder(USER_ID, ORDER_ID)).thenReturn(order);
            when(paymentRepository.findByOrderIdWithPessimisticWriteLock(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PaymentCreateResponse response = paymentCreationService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.READY);
            assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(response.getAmount()).isEqualTo(AMOUNT);
            assertThat(response.getPgProvider()).isEqualTo(PgProvider.TOSS);
            assertThat(response.getPgOrderId()).isEqualTo(ORDER_NO);
            assertThat(response.getPaymentDeadline()).isEqualTo(PAYMENT_DEADLINE);
            verify(paymentOrderPolicy).ensurePayable(null, order);
            verify(paymentRepository).save(any(Payment.class));
        }
    }
}
