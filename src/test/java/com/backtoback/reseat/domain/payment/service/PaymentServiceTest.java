package com.backtoback.reseat.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.domain.payment.dto.request.PaymentRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCreateResponse;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyRequiredException;
import com.backtoback.reseat.domain.payment.exception.PaymentLockFailedException;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import com.backtoback.reseat.domain.payment.repository.PaymentRecoveryTaskRepository;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 결제 처리")
class PaymentServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final String PAYMENT_LOCK_KEY = "payment:create:order:" + ORDER_ID;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentRecoveryTaskRepository paymentRecoveryTaskRepository;

    @Mock
    private PaymentCreationService paymentCreationService;

    @Mock
    private PaymentOrderPolicy paymentOrderPolicy;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private PaymentServiceValidator paymentValidator;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentRequest request() {
        PaymentRequest request = mock(PaymentRequest.class);
        when(request.getOrderId()).thenReturn(ORDER_ID);
        return request;
    }

    private RLock lock() {
        RLock lock = mock(RLock.class);
        when(redissonClient.getLock(PAYMENT_LOCK_KEY)).thenReturn(lock);
        return lock;
    }

    @Nested
    @DisplayName("주문 기준 결제를 요청한다")
    class RequestPayment {

        @Test
        @DisplayName("주문 락을 획득하면 결제 생성 서비스의 결과를 반환하고 락을 해제한다.")
        void returnsCreationResultAndUnlocks() throws InterruptedException {
            PaymentRequest request = request();
            RLock lock = lock();
            PaymentCreateResponse expected = mock(PaymentCreateResponse.class);
            when(lock.tryLock(3L, TimeUnit.SECONDS)).thenReturn(true);
            when(lock.isHeldByCurrentThread()).thenReturn(true);
            when(paymentCreationService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request))
                    .thenReturn(expected);

            PaymentCreateResponse response =
                    paymentService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request);

            assertThat(response).isSameAs(expected);
            verify(paymentValidator).validateIdempotencyKey(IDEMPOTENCY_KEY);
            verify(redissonClient).getLock(PAYMENT_LOCK_KEY);
            verify(paymentCreationService).requestPayment(USER_ID, IDEMPOTENCY_KEY, request);
            verify(lock).unlock();
        }

        @Test
        @DisplayName("주문 락을 획득하지 못하면 결제 생성 없이 락 실패 예외가 발생한다.")
        void rejectsWhenLockCannotBeAcquired() throws InterruptedException {
            PaymentRequest request = request();
            RLock lock = lock();
            when(lock.tryLock(3L, TimeUnit.SECONDS)).thenReturn(false);

            assertThatThrownBy(() ->
                    paymentService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request))
                    .isInstanceOf(PaymentLockFailedException.class);

            verifyNoInteractions(paymentCreationService);
            verify(lock, never()).unlock();
        }

        @Test
        @DisplayName("결제 생성 중 DB 충돌이 발생하면 락 안에서 기존 결제를 다시 조회한다.")
        void retriesCreationAfterDatabaseConflict() throws InterruptedException {
            PaymentRequest request = request();
            RLock lock = lock();
            PaymentCreateResponse expected = mock(PaymentCreateResponse.class);
            when(lock.tryLock(3L, TimeUnit.SECONDS)).thenReturn(true);
            when(lock.isHeldByCurrentThread()).thenReturn(true);
            when(paymentCreationService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request))
                    .thenThrow(new DataIntegrityViolationException("order unique constraint"))
                    .thenReturn(expected);

            PaymentCreateResponse response =
                    paymentService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request);

            assertThat(response).isSameAs(expected);
            verify(paymentCreationService, times(2))
                    .requestPayment(USER_ID, IDEMPOTENCY_KEY, request);
            verify(lock).unlock();
        }

        @Test
        @DisplayName("락 대기 중 인터럽트되면 인터럽트 상태를 복구하고 락 실패 예외를 발생시킨다.")
        void restoresInterruptStatusWhenLockWaitIsInterrupted() throws InterruptedException {
            PaymentRequest request = request();
            RLock lock = lock();
            when(lock.tryLock(3L, TimeUnit.SECONDS)).thenThrow(new InterruptedException());

            try {
                assertThatThrownBy(() ->
                        paymentService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request))
                        .isInstanceOf(PaymentLockFailedException.class);

                assertThat(Thread.currentThread().isInterrupted()).isTrue();
                verifyNoInteractions(paymentCreationService);
                verify(lock, never()).unlock();
            } finally {
                Thread.interrupted();
            }
        }

        @Test
        @DisplayName("결제 생성 처리에서 예외가 발생해도 보유 중인 주문 락을 해제한다.")
        void unlocksWhenCreationFails() throws InterruptedException {
            PaymentRequest request = request();
            RLock lock = lock();
            RuntimeException exception = new RuntimeException("결제 생성 실패");
            when(lock.tryLock(3L, TimeUnit.SECONDS)).thenReturn(true);
            when(lock.isHeldByCurrentThread()).thenReturn(true);
            when(paymentCreationService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request))
                    .thenThrow(exception);

            assertThatThrownBy(() ->
                    paymentService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request))
                    .isSameAs(exception);

            verify(lock).unlock();
        }

        @Test
        @DisplayName("멱등키 검증에 실패하면 주문 락과 결제 생성을 시도하지 않는다.")
        void rejectsInvalidIdempotencyKeyBeforeLocking() {
            String invalidIdempotencyKey = "";
            PaymentRequest request = mock(PaymentRequest.class);
            doThrow(new IdempotencyKeyRequiredException())
                    .when(paymentValidator)
                    .validateIdempotencyKey(invalidIdempotencyKey);

            assertThatThrownBy(() ->
                    paymentService.requestPayment(USER_ID, invalidIdempotencyKey, request))
                    .isInstanceOf(IdempotencyKeyRequiredException.class);

            verifyNoInteractions(redissonClient, paymentCreationService);
        }
    }
}
