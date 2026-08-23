package com.backtoback.reseat.domain.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.exception.OrderExpiredException;
import com.backtoback.reseat.domain.order.repository.OrderItemRepository;
import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCancelRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCompleteRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentFailRequest;
import com.backtoback.reseat.domain.payment.dto.request.PaymentRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentActionResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCancelResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCompleteResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCreateResponse;
import com.backtoback.reseat.domain.payment.dto.response.PaymentResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryStatus;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyRequiredException;
import com.backtoback.reseat.domain.payment.exception.PaymentAccessDeniedException;
import com.backtoback.reseat.domain.payment.exception.PaymentAlreadyFinalizedException;
import com.backtoback.reseat.domain.payment.exception.PaymentCallbackMismatchException;
import com.backtoback.reseat.domain.payment.exception.PaymentCancelFailedException;
import com.backtoback.reseat.domain.payment.exception.PaymentCancelNotAllowedException;
import com.backtoback.reseat.domain.payment.exception.PaymentLockFailedException;
import com.backtoback.reseat.domain.payment.exception.PaymentNotFoundException;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;
import com.backtoback.reseat.domain.payment.pg.toss.exception.TossPaymentStatusUnknownException;
import com.backtoback.reseat.domain.payment.repository.PaymentRecoveryTaskRepository;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;
import com.backtoback.reseat.domain.ticket.service.TicketService;
import com.backtoback.reseat.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 결제 처리")
class PaymentServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final Long PAYMENT_ID = 100L;
    private static final Long ORDER_ITEM_ID = 1000L;
    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final String PAYMENT_KEY = "payment-key";
    private static final String PG_ORDER_ID = "ORD-20260728-000001";
    private static final int AMOUNT = 10000;
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

    @Mock
    private ObjectProvider<TicketService> ticketServiceProvider;

    @Mock
    private TicketService ticketService;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private TicketRepository ticketRepository;

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

    private Payment payment(PaymentStatus status) {
        Order order = mock(Order.class);

        return Payment
            .builder()
            .paymentNo("PAY-20260728010000-000001")
            .order(order)
            .user(mock(User.class))
            .amount(AMOUNT)
            .idempotencyKey(IDEMPOTENCY_KEY)
            .status(status)
            .pgProvider(PgProvider.TOSS)
            .pgOrderId(PG_ORDER_ID)
            .build();
    }

    private PaymentCompleteRequest completeRequest() {
        PaymentCompleteRequest request = mock(PaymentCompleteRequest.class);
        when(request.getPaymentKey()).thenReturn(PAYMENT_KEY);
        when(request.getOrderId()).thenReturn(PG_ORDER_ID);
        when(request.getAmount()).thenReturn(AMOUNT);
        return request;
    }

    private void givenIssuedTicket(Payment payment, Ticket ticket) {
        OrderItem orderItem = mock(OrderItem.class);
        when(payment.getOrder().getId()).thenReturn(ORDER_ID);
        when(orderItem.getId()).thenReturn(ORDER_ITEM_ID);
        when(orderItemRepository.findByOrder_Id(ORDER_ID)).thenReturn(List.of(orderItem));
        when(ticketRepository.findByOrderItemId(ORDER_ITEM_ID)).thenReturn(Optional.of(ticket));
    }

    private OrderItem orderItem() {
        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getId()).thenReturn(ORDER_ITEM_ID);
        return orderItem;
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
            when(paymentCreationService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request)).thenReturn(expected);

            PaymentCreateResponse response = paymentService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request);

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

            assertThatThrownBy(() -> paymentService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request))
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

            PaymentCreateResponse response = paymentService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request);

            assertThat(response).isSameAs(expected);
            verify(paymentCreationService, times(2)).requestPayment(USER_ID, IDEMPOTENCY_KEY, request);
            verify(lock).unlock();
        }

        @Test
        @DisplayName("락 대기 중 인터럽트되면 인터럽트 상태를 복구하고 락 실패 예외를 발생시킨다.")
        void restoresInterruptStatusWhenLockWaitIsInterrupted() throws InterruptedException {
            PaymentRequest request = request();
            RLock lock = lock();
            when(lock.tryLock(3L, TimeUnit.SECONDS)).thenThrow(new InterruptedException());

            try {
                assertThatThrownBy(() -> paymentService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request))
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
            when(paymentCreationService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request)).thenThrow(exception);

            assertThatThrownBy(() -> paymentService.requestPayment(USER_ID, IDEMPOTENCY_KEY, request))
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

            assertThatThrownBy(() -> paymentService.requestPayment(USER_ID, invalidIdempotencyKey, request))
                .isInstanceOf(IdempotencyKeyRequiredException.class);

            verifyNoInteractions(redissonClient, paymentCreationService);
        }
    }

    @Nested
    @DisplayName("토스 결제를 승인한다")
    class CompletePayment {

        @Test
        @DisplayName("Toss 승인이 완료되면 결제와 주문을 완료 처리한다.")
        void approvesPaymentAndCompletesOrder() {
            Payment payment = payment(PaymentStatus.READY);
            Ticket ticket = mock(Ticket.class, RETURNS_DEEP_STUBS);
            OrderItem orderItem = orderItem();
            PaymentCompleteRequest request = completeRequest();
            TossPaymentResponse tossResponse = mock(TossPaymentResponse.class);
            when(payment.getOrder().getId()).thenReturn(ORDER_ID);
            when(payment.getOrder().getStatus()).thenReturn(OrderStatus.PAID);
            when(ticketServiceProvider.getObject()).thenReturn(ticketService);
            when(orderItemRepository.findByOrder_Id(ORDER_ID)).thenReturn(List.of(orderItem));
            when(ticketRepository.findByOrderItemId(ORDER_ITEM_ID)).thenReturn(Optional.empty());
            when(ticketService.issue(payment.getOrder())).thenReturn(List.of(ticket));
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(tossResponse.isApproved()).thenReturn(true);
            when(tossResponse.getPaymentKey()).thenReturn(PAYMENT_KEY);
            when(tossResponse.getMethod()).thenReturn("CARD");
            when(tossResponse.getApprovedAt()).thenReturn("2026-07-28T12:00:00+09:00");
            when(tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT)).thenReturn(tossResponse);

            PaymentCompleteResponse response
                = paymentService.completePayment(USER_ID, PAYMENT_ID, IDEMPOTENCY_KEY, request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(response.getPaymentNo()).isEqualTo(payment.getPaymentNo());
            assertThat(response.getMethod()).isEqualTo("CARD");
            assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(response.getTickets()).isNotEmpty();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.getPgPaymentKey()).isEqualTo(PAYMENT_KEY);
            assertThat(payment.getMethod()).isEqualTo("CARD");
            assertThat(payment.getApprovedAt()).isEqualTo(LocalDateTime.of(2026, 7, 28, 12, 0));
            verify(paymentValidator).validateOwner(payment, USER_ID);
            verify(paymentValidator).validateActiveIdempotencyKey(payment, IDEMPOTENCY_KEY);
            verify(paymentValidator).validateConfirmable(payment, PG_ORDER_ID, AMOUNT);
            verify(paymentOrderPolicy).ensurePayable(payment, payment.getOrder());
            verify(tossPaymentClient).confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT);
            verify(orderService).completeOrder(ORDER_ID);
            verify(ticketService).issue(payment.getOrder());
            verify(paymentRecoveryTaskRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 실패한 결제에 승인 완료를 요청하면 이미 종결된 결제 예외를 던진다.")
        void rejectsFailedPayment() {
            Payment payment = payment(PaymentStatus.FAILED);
            PaymentCompleteRequest request = mock(PaymentCompleteRequest.class);
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.completePayment(USER_ID, PAYMENT_ID, IDEMPOTENCY_KEY, request))
                .isInstanceOf(PaymentAlreadyFinalizedException.class);

            verify(paymentValidator).validateOwner(payment, USER_ID);
            verify(paymentValidator).validateActiveIdempotencyKey(payment, IDEMPOTENCY_KEY);
            verifyNoInteractions(paymentOrderPolicy, tossPaymentClient, orderService, paymentRecoveryTaskRepository);
        }

        @Test
        @DisplayName("취소된 결제에 승인 완료를 요청하면 이미 종결된 결제 예외를 던진다.")
        void rejectsCanceledPayment() {
            Payment payment = payment(PaymentStatus.CANCELED);
            PaymentCompleteRequest request = mock(PaymentCompleteRequest.class);
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.completePayment(USER_ID, PAYMENT_ID, IDEMPOTENCY_KEY, request))
                .isInstanceOf(PaymentAlreadyFinalizedException.class);

            verify(paymentValidator).validateOwner(payment, USER_ID);
            verify(paymentValidator).validateActiveIdempotencyKey(payment, IDEMPOTENCY_KEY);
            verifyNoInteractions(paymentOrderPolicy, tossPaymentClient, orderService, paymentRecoveryTaskRepository);
        }

        @Test
        @DisplayName("이미 승인된 결제에 모든 티켓이 있으면 Toss 승인과 티켓 발급을 반복하지 않는다.")
        void returnsExistingTicketsForApprovedPayment() {
            Payment payment = payment(PaymentStatus.APPROVED);
            Ticket ticket = mock(Ticket.class, RETURNS_DEEP_STUBS);
            PaymentCompleteRequest request = mock(PaymentCompleteRequest.class);
            givenIssuedTicket(payment, ticket);
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));

            PaymentCompleteResponse response
                = paymentService.completePayment(USER_ID, PAYMENT_ID, IDEMPOTENCY_KEY, request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(response.getTickets()).isNotEmpty();
            verifyNoInteractions(paymentOrderPolicy, tossPaymentClient, orderService, ticketService);
        }

        @Test
        @DisplayName("이미 승인된 결제의 티켓이 누락되면 Toss 승인 없이 누락 티켓 발급을 다시 시도한다.")
        void issuesMissingTicketsForApprovedPayment() {
            Payment payment = payment(PaymentStatus.APPROVED);
            Ticket ticket = mock(Ticket.class, RETURNS_DEEP_STUBS);
            OrderItem orderItem = orderItem();
            PaymentCompleteRequest request = mock(PaymentCompleteRequest.class);
            when(payment.getOrder().getId()).thenReturn(ORDER_ID);
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(orderItemRepository.findByOrder_Id(ORDER_ID)).thenReturn(List.of(orderItem));
            when(ticketRepository.findByOrderItemId(ORDER_ITEM_ID)).thenReturn(Optional.empty());
            when(ticketServiceProvider.getObject()).thenReturn(ticketService);
            when(ticketService.issue(payment.getOrder())).thenReturn(List.of(ticket));

            PaymentCompleteResponse response
                = paymentService.completePayment(USER_ID, PAYMENT_ID, IDEMPOTENCY_KEY, request);

            assertThat(response.getTickets()).hasSize(1);
            verify(ticketService).issue(payment.getOrder());
            verifyNoInteractions(paymentOrderPolicy, tossPaymentClient, orderService);
        }

        @Test
        @DisplayName("티켓 발급 중 오류가 발생하면 원래 예외를 그대로 전파한다.")
        void propagatesTicketIssuanceFailure() {
            Payment payment = payment(PaymentStatus.APPROVED);
            OrderItem orderItem = orderItem();
            PaymentCompleteRequest request = mock(PaymentCompleteRequest.class);
            RuntimeException cause = new RuntimeException("티켓 저장 실패");
            when(payment.getOrder().getId()).thenReturn(ORDER_ID);
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(orderItemRepository.findByOrder_Id(ORDER_ID)).thenReturn(List.of(orderItem));
            when(ticketRepository.findByOrderItemId(ORDER_ITEM_ID)).thenReturn(Optional.empty());
            when(ticketServiceProvider.getObject()).thenReturn(ticketService);
            doThrow(cause).when(ticketService).issue(payment.getOrder());

            assertThatThrownBy(() -> paymentService.completePayment(USER_ID, PAYMENT_ID, IDEMPOTENCY_KEY, request))
                .isSameAs(cause);
        }

        @Test
        @DisplayName("Toss가 명시적인 승인 실패 상태를 반환하면 결제와 주문을 실패 처리한다.")
        void failsPaymentAndOrderForRejectedConfirm() {
            Payment payment = payment(PaymentStatus.READY);
            PaymentCompleteRequest request = completeRequest();
            TossPaymentResponse tossResponse = mock(TossPaymentResponse.class);
            when(payment.getOrder().getId()).thenReturn(ORDER_ID);
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(tossResponse.isApproved()).thenReturn(false);
            when(tossResponse.getStatus()).thenReturn("ABORTED");
            when(tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT)).thenReturn(tossResponse);

            PaymentCompleteResponse response
                = paymentService.completePayment(USER_ID, PAYMENT_ID, IDEMPOTENCY_KEY, request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(response.getTickets()).isEmpty();
            assertThat(payment.getFailReason()).isEqualTo("토스 결제 승인 상태가 완료가 아닙니다. status=ABORTED");
            verify(orderService).failOrder(ORDER_ID);
            verify(orderService, never()).completeOrder(any());
            verify(paymentRecoveryTaskRepository, never()).save(any());
        }

        @Test
        @DisplayName("Toss 승인 상태를 확인할 수 없으면 결제와 주문을 실패 처리하고 복구 작업을 등록한다.")
        void registersRecoveryTaskForUnknownConfirmStatus() {
            Payment payment = payment(PaymentStatus.READY);
            PaymentCompleteRequest request = completeRequest();
            when(payment.getOrder().getId()).thenReturn(ORDER_ID);
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT))
                .thenThrow(new TossPaymentStatusUnknownException("승인", new RuntimeException("Toss 응답 없음")));

            PaymentCompleteResponse response
                = paymentService.completePayment(USER_ID, PAYMENT_ID, IDEMPOTENCY_KEY, request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(response.getTickets()).isEmpty();
            assertThat(payment.getPgPaymentKey()).isEqualTo(PAYMENT_KEY);
            assertThat(payment.getFailReason()).isEqualTo("토스 결제 승인 상태를 확인할 수 없습니다.");
            ArgumentCaptor<PaymentRecoveryTask> taskCaptor = ArgumentCaptor.forClass(PaymentRecoveryTask.class);
            verify(paymentRecoveryTaskRepository).save(taskCaptor.capture());
            assertThat(taskCaptor.getValue().getPayment()).isSameAs(payment);
            assertThat(taskCaptor.getValue().getStatus()).isEqualTo(PaymentRecoveryStatus.PENDING);
            verify(orderService).failOrder(ORDER_ID);
            verify(orderService, never()).completeOrder(any());
        }

        @Test
        @DisplayName("콜백 정보 검증에 실패하면 주문 정책과 Toss 승인을 호출하지 않는다.")
        void rejectsInvalidCallbackBeforeConfirm() {
            Payment payment = payment(PaymentStatus.READY);
            PaymentCompleteRequest request = mock(PaymentCompleteRequest.class);
            when(request.getOrderId()).thenReturn(PG_ORDER_ID);
            when(request.getAmount()).thenReturn(AMOUNT);
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
            doThrow(new PaymentCallbackMismatchException())
                .when(paymentValidator)
                .validateConfirmable(payment, PG_ORDER_ID, AMOUNT);

            assertThatThrownBy(() -> paymentService.completePayment(USER_ID, PAYMENT_ID, IDEMPOTENCY_KEY, request))
                .isInstanceOf(PaymentCallbackMismatchException.class);

            verifyNoInteractions(paymentOrderPolicy, tossPaymentClient, orderService, paymentRecoveryTaskRepository);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        }

        @Test
        @DisplayName("주문 결제 기한이 만료되면 Toss 승인을 호출하지 않는다.")
        void rejectsExpiredOrderBeforeConfirm() {
            Payment payment = payment(PaymentStatus.READY);
            PaymentCompleteRequest request = mock(PaymentCompleteRequest.class);
            when(request.getOrderId()).thenReturn(PG_ORDER_ID);
            when(request.getAmount()).thenReturn(AMOUNT);
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
            doThrow(new OrderExpiredException()).when(paymentOrderPolicy).ensurePayable(payment, payment.getOrder());

            assertThatThrownBy(() -> paymentService.completePayment(USER_ID, PAYMENT_ID, IDEMPOTENCY_KEY, request))
                .isInstanceOf(OrderExpiredException.class);

            verifyNoInteractions(tossPaymentClient, orderService, paymentRecoveryTaskRepository);
            assertThat(payment.getPgPaymentKey()).isNull();
        }
    }

    @Nested
    @DisplayName("결제 실패 콜백을 처리한다")
    class FailPayment {

        @Test
        @DisplayName("READY 결제를 실패 처리하고 주문에 실패 상태를 전파한다.")
        void failsReadyPaymentAndOrder() {
            Payment payment = payment(PaymentStatus.READY);
            PaymentFailRequest request = mock(PaymentFailRequest.class);
            when(payment.getOrder().getId()).thenReturn(ORDER_ID);
            when(request.getOrderId()).thenReturn(PG_ORDER_ID);
            when(request.getCode()).thenReturn("PAY_PROCESS_CANCELED");
            when(request.getMessage()).thenReturn("사용자가 결제를 취소했습니다.");
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));

            PaymentActionResponse response = paymentService.failPayment(USER_ID, PAYMENT_ID, IDEMPOTENCY_KEY, request);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailReason()).isEqualTo("[PAY_PROCESS_CANCELED] 사용자가 결제를 취소했습니다.");
            assertThat(payment.getFailedAt()).isNotNull();
            verify(paymentValidator).validateOwner(payment, USER_ID);
            verify(paymentValidator).validateActiveIdempotencyKey(payment, IDEMPOTENCY_KEY);
            verify(paymentValidator).validateFailable(payment);
            verify(paymentValidator).validatePgOrderId(payment, PG_ORDER_ID);
            verify(orderService).failOrder(ORDER_ID);
        }

        @ParameterizedTest(name = "{0} 결제의 기존 결과를 반환한다")
        @EnumSource(
            value = PaymentStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "READY"
        )
        @DisplayName("이미 종결된 결제는 상태를 변경하지 않고 기존 결과를 반환한다.")
        void returnsFinalizedPaymentWithoutFailingOrder(PaymentStatus status) {
            Payment payment = payment(status);
            PaymentFailRequest request = mock(PaymentFailRequest.class);
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));

            PaymentActionResponse response = paymentService.failPayment(USER_ID, PAYMENT_ID, IDEMPOTENCY_KEY, request);

            assertThat(response.getStatus()).isEqualTo(status);
            assertThat(payment.getStatus()).isEqualTo(status);
            verify(paymentValidator).validateOwner(payment, USER_ID);
            verify(paymentValidator).validateActiveIdempotencyKey(payment, IDEMPOTENCY_KEY);
            verify(paymentValidator, never()).validateFailable(any());
            verify(paymentValidator, never()).validatePgOrderId(any(), any());
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("콜백 주문번호가 다르면 결제와 주문을 실패 처리하지 않는다.")
        void rejectsMismatchedOrderIdBeforeFailingPayment() {
            Payment payment = payment(PaymentStatus.READY);
            PaymentFailRequest request = mock(PaymentFailRequest.class);
            when(request.getOrderId()).thenReturn("different-order-id");
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
            doThrow(new PaymentCallbackMismatchException())
                .when(paymentValidator)
                .validatePgOrderId(payment, "different-order-id");

            assertThatThrownBy(() -> paymentService.failPayment(USER_ID, PAYMENT_ID, IDEMPOTENCY_KEY, request))
                .isInstanceOf(PaymentCallbackMismatchException.class);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
            assertThat(payment.getFailReason()).isNull();
            verify(paymentValidator).validateFailable(payment);
            verifyNoInteractions(orderService);
        }
    }

    @Nested
    @DisplayName("승인된 결제를 취소한다")
    class CancelPayment {

        @Test
        @DisplayName("Toss 취소가 완료되면 결제와 주문을 취소 처리한다.")
        void cancelsApprovedPaymentAndOrder() {
            Payment payment = payment(PaymentStatus.APPROVED);
            PaymentCancelRequest request = new PaymentCancelRequest("사용자 요청");
            TossPaymentResponse tossResponse = mock(TossPaymentResponse.class);
            when(payment.getOrder().getId()).thenReturn(ORDER_ID);
            payment.assignPgPaymentKey(PAYMENT_KEY);
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(tossPaymentClient.cancel(PAYMENT_KEY, request.getCancelReason())).thenReturn(tossResponse);
            when(tossResponse.isCancelCompleted()).thenReturn(true);

            PaymentCancelResponse response = paymentService.cancelPayment(USER_ID, PAYMENT_ID, request);

            assertThat(response.getRefundAmount()).isEqualTo(AMOUNT);
            assertThat(response.getCanceledAmount()).isEqualTo(AMOUNT);
            assertThat(response.getRemainingAmount()).isZero();
            assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
            verify(paymentValidator).validateOwner(payment, USER_ID);
            verify(paymentValidator).validateCancelable(payment);
            verify(tossPaymentClient).cancel(PAYMENT_KEY, "사용자 요청");
            verify(orderService).cancelPaidOrder(ORDER_ID);
        }

        @Test
        @DisplayName("이미 취소된 결제는 Toss를 호출하지 않고 기존 결과를 반환한다.")
        void returnsCanceledPaymentWithoutCancelingAgain() {
            Payment payment = payment(PaymentStatus.CANCELED);
            PaymentCancelRequest request = new PaymentCancelRequest("중복 요청");
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));

            PaymentCancelResponse response = paymentService.cancelPayment(USER_ID, PAYMENT_ID, request);

            assertThat(response.getRefundAmount()).isEqualTo(AMOUNT);
            assertThat(response.getCanceledAmount()).isEqualTo(AMOUNT);
            assertThat(response.getRemainingAmount()).isZero();
            assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
            verify(paymentValidator).validateOwner(payment, USER_ID);
            verify(paymentValidator, never()).validateCancelable(any());
            verifyNoInteractions(tossPaymentClient, orderService);
        }

        @ParameterizedTest(name = "{0} 결제는 취소할 수 없다")
        @EnumSource(
            value = PaymentStatus.class,
            names = {
                "READY",
                "FAILED"
            }
        )
        @DisplayName("승인되지 않은 결제는 Toss 취소를 호출하지 않는다.")
        void rejectsUnapprovedPayment(PaymentStatus status) {
            Payment payment = payment(status);
            PaymentCancelRequest request = new PaymentCancelRequest("사용자 요청");
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
            doThrow(new PaymentCancelNotAllowedException()).when(paymentValidator).validateCancelable(payment);

            assertThatThrownBy(() -> paymentService.cancelPayment(USER_ID, PAYMENT_ID, request))
                .isInstanceOf(PaymentCancelNotAllowedException.class);

            assertThat(payment.getStatus()).isEqualTo(status);
            verifyNoInteractions(tossPaymentClient, orderService);
        }

        @Test
        @DisplayName("Toss 취소 상태를 확인할 수 없으면 로컬 결제를 승인 상태로 유지한다.")
        void keepsApprovedPaymentWhenCancelStatusIsUnknown() {
            Payment payment = payment(PaymentStatus.APPROVED);
            PaymentCancelRequest request = new PaymentCancelRequest("사용자 요청");
            payment.assignPgPaymentKey(PAYMENT_KEY);
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(tossPaymentClient.cancel(PAYMENT_KEY, request.getCancelReason()))
                .thenThrow(new TossPaymentStatusUnknownException("취소", new RuntimeException("Toss 응답 없음")));

            assertThatThrownBy(() -> paymentService.cancelPayment(USER_ID, PAYMENT_ID, request))
                .isInstanceOf(PaymentCancelFailedException.class);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("Toss 응답이 취소 완료 상태가 아니면 로컬 결제를 승인 상태로 유지한다.")
        void keepsApprovedPaymentWhenCancelIsNotCompleted() {
            Payment payment = payment(PaymentStatus.APPROVED);
            PaymentCancelRequest request = new PaymentCancelRequest("사용자 요청");
            TossPaymentResponse tossResponse = mock(TossPaymentResponse.class);
            payment.assignPgPaymentKey(PAYMENT_KEY);
            when(paymentRepository.findByIdWithPessimisticWriteLock(PAYMENT_ID)).thenReturn(Optional.of(payment));
            when(tossPaymentClient.cancel(PAYMENT_KEY, request.getCancelReason())).thenReturn(tossResponse);
            when(tossResponse.isCancelCompleted()).thenReturn(false);
            when(tossResponse.getStatus()).thenReturn("DONE");

            assertThatThrownBy(() -> paymentService.cancelPayment(USER_ID, PAYMENT_ID, request))
                .isInstanceOf(PaymentCancelFailedException.class);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            verifyNoInteractions(orderService);
        }
    }

    @Nested
    @DisplayName("결제 상세 정보를 조회한다")
    class GetPayment {

        @Test
        @DisplayName("소유한 결제를 조회하고 상세 정보를 반환한다.")
        void returnsOwnedPaymentWithoutLock() {
            Payment payment = payment(PaymentStatus.APPROVED);
            when(payment.getOrder().getId()).thenReturn(ORDER_ID);
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

            PaymentResponse response = paymentService.getPayment(USER_ID, PAYMENT_ID);

            assertThat(response.getPaymentNo()).isEqualTo(payment.getPaymentNo());
            assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(response.getAmount()).isEqualTo(AMOUNT);
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(response.getPgProvider()).isEqualTo(PgProvider.TOSS);
            verify(paymentValidator).validateOwner(payment, USER_ID);
            verify(paymentRepository, never()).findByIdWithPessimisticWriteLock(any());
        }

        @Test
        @DisplayName("결제가 존재하지 않으면 조회 실패 예외가 발생한다.")
        void rejectsMissingPayment() {
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPayment(USER_ID, PAYMENT_ID))
                .isInstanceOf(PaymentNotFoundException.class);

            verifyNoInteractions(paymentValidator);
            verify(paymentRepository, never()).findByIdWithPessimisticWriteLock(any());
        }

        @Test
        @DisplayName("타인의 결제는 상세 정보를 반환하지 않는다.")
        void rejectsPaymentOwnedByAnotherUser() {
            Payment payment = payment(PaymentStatus.APPROVED);
            when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
            doThrow(new PaymentAccessDeniedException()).when(paymentValidator).validateOwner(payment, USER_ID);

            assertThatThrownBy(() -> paymentService.getPayment(USER_ID, PAYMENT_ID))
                .isInstanceOf(PaymentAccessDeniedException.class);

            verify(paymentRepository, never()).findByIdWithPessimisticWriteLock(any());
        }
    }
}
