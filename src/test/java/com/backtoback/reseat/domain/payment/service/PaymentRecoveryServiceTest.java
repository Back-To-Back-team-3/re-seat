package com.backtoback.reseat.domain.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentCancel;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryStatus;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryType;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;
import com.backtoback.reseat.domain.payment.repository.PaymentRecoveryTaskRepository;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.payment.schedule.ConfirmUnknownRecoveryHandler;
import com.backtoback.reseat.domain.payment.schedule.PaymentRecoveryHandler;
import com.backtoback.reseat.domain.payment.schedule.PaymentRecoveryResult;
import com.backtoback.reseat.domain.payment.schedule.PaymentRecoveryService;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRecoveryService 결제 승인 복구")
class PaymentRecoveryServiceTest {

    private static final Long TASK_ID = 1L;
    private static final String PAYMENT_KEY = "payment-key";
    private static final String RECOVERY_CANCEL_REASON = "승인 상태 불명확 결제 자동 환불";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 28, 12, 0);

    @Mock
    private PaymentRecoveryTaskRepository paymentRecoveryTaskRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private TransactionTemplate transactionTemplate;
    private PaymentRecoveryService paymentRecoveryService;

    @BeforeEach
    void setUp() {
        lenient()
            .when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenReturn(transactionStatus);
        transactionTemplate = new TransactionTemplate(transactionManager);
        paymentRecoveryService
            = new PaymentRecoveryService(
                paymentRecoveryTaskRepository,
                paymentRepository,
                transactionTemplate,
                List.of(new ConfirmUnknownRecoveryHandler(tossPaymentClient))
            );
    }

    private PaymentRecoveryTask pendingTask() {
        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(1L);
        return PaymentRecoveryTask.createConfirmUnknown(payment);
    }

    private PaymentRecoveryTask confirmRecoveryTask() {
        PaymentRecoveryTask task = pendingTask();
        when(task.getPayment().getPgPaymentKey()).thenReturn(PAYMENT_KEY);
        return task;
    }

    private PaymentRecoveryTask partialCancelTask() {
        Payment payment = mock(Payment.class);
        PaymentCancel paymentCancel = mock(PaymentCancel.class);
        lenient().when(payment.getId()).thenReturn(2L);
        when(paymentCancel.getId()).thenReturn(1L);
        when(paymentCancel.getPayment()).thenReturn(payment);
        return PaymentRecoveryTask.createPartialCancel(paymentCancel);
    }

    @Nested
    @DisplayName("복구 유형별 Handler를 등록한다")
    class RegisterHandlers {

        @Test
        @DisplayName("같은 복구 유형을 지원하는 Handler가 중복되면 예외가 발생한다.")
        void rejectsDuplicateHandlerType() {
            PaymentRecoveryHandler firstHandler = mock(PaymentRecoveryHandler.class);
            PaymentRecoveryHandler secondHandler = mock(PaymentRecoveryHandler.class);
            when(firstHandler.getType()).thenReturn(PaymentRecoveryType.CONFIRM_UNKNOWN);
            when(secondHandler.getType()).thenReturn(PaymentRecoveryType.CONFIRM_UNKNOWN);

            assertThatThrownBy(
                () -> new PaymentRecoveryService(
                    paymentRecoveryTaskRepository,
                    paymentRepository,
                    transactionTemplate,
                    List.of(firstHandler, secondHandler)
                )
            ).isInstanceOf(IllegalStateException.class).hasMessage("결제 복구 Handler가 중복 등록되었습니다: CONFIRM_UNKNOWN");
        }
    }

    @Nested
    @DisplayName("승인 상태가 불명확한 결제를 복구한다")
    class Recover {

        @Test
        @DisplayName("복구 작업이 존재하지 않으면 Toss를 호출하지 않는다.")
        void ignoresMissingTask() {
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID)).thenReturn(Optional.empty());

            paymentRecoveryService.recover(TASK_ID, NOW);

            verifyNoInteractions(tossPaymentClient);
        }

        @Test
        @DisplayName("이미 완료된 작업은 다시 처리하지 않는다.")
        void ignoresCompletedTask() {
            PaymentRecoveryTask task = pendingTask();
            task.startProcessing(NOW.minusMinutes(2));
            task.complete(NOW.minusMinutes(1));
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID)).thenReturn(Optional.of(task));

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.COMPLETED);
            verifyNoInteractions(tossPaymentClient);
        }

        @Test
        @DisplayName("재시도 시각이 지나지 않은 작업은 처리하지 않는다.")
        void ignoresRetryTaskBeforeDueTime() {
            PaymentRecoveryTask task = pendingTask();
            task.startProcessing(NOW.minusMinutes(1));
            task.scheduleRetry("토스 조회 실패", NOW.plusMinutes(1));
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID)).thenReturn(Optional.of(task));

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.RETRY);
            assertThat(task.getAttemptCount()).isEqualTo(1);
            verifyNoInteractions(tossPaymentClient);
        }

        @Test
        @DisplayName("등록된 처리기가 없는 복구 유형은 최종 실패 처리한다.")
        void failsTaskWhenHandlerIsMissing() {
            PaymentRecoveryTask task = partialCancelTask();
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID)).thenReturn(Optional.of(task));

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.FAILED);
            assertThat(task.getLastError()).isEqualTo("지원하지 않는 결제 복구 유형입니다: PARTIAL_CANCEL");
            verifyNoInteractions(tossPaymentClient);
        }

        @Test
        @DisplayName("처리기가 종결 실패를 반환하면 재시도를 예약하지 않고 작업을 실패 처리한다.")
        void failsTaskForNonRetryableResult() {
            PaymentRecoveryTask task = partialCancelTask();
            PaymentRecoveryHandler handler = mock(PaymentRecoveryHandler.class);
            when(handler.getType()).thenReturn(PaymentRecoveryType.PARTIAL_CANCEL);
            when(handler.recover(task)).thenReturn(PaymentRecoveryResult.failure("Toss가 취소 요청을 거절했습니다."));
            PaymentRecoveryService service
                = new PaymentRecoveryService(
                    paymentRecoveryTaskRepository,
                    paymentRepository,
                    transactionTemplate,
                    List.of(handler)
                );
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID)).thenReturn(Optional.of(task));
            when(paymentRepository.findByIdWithPessimisticWriteLock(2L)).thenReturn(Optional.of(task.getPayment()));

            service.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.FAILED);
            assertThat(task.getAttemptCount()).isZero();
            assertThat(task.getNextRetryAt()).isNull();
            assertThat(task.getLastError()).isEqualTo("Toss가 취소 요청을 거절했습니다.");
        }

        @Test
        @DisplayName("부분 취소 복구는 공유 결제를 잠근 뒤 처리기를 실행한다.")
        void locksPaymentBeforePartialCancelRecovery() {
            PaymentRecoveryTask task = partialCancelTask();
            PaymentRecoveryHandler handler = mock(PaymentRecoveryHandler.class);
            when(handler.getType()).thenReturn(PaymentRecoveryType.PARTIAL_CANCEL);
            when(handler.recover(task)).thenReturn(PaymentRecoveryResult.success());
            PaymentRecoveryService service
                = new PaymentRecoveryService(
                    paymentRecoveryTaskRepository,
                    paymentRepository,
                    transactionTemplate,
                    List.of(handler)
                );
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID)).thenReturn(Optional.of(task));
            when(paymentRepository.findByIdWithPessimisticWriteLock(2L)).thenReturn(Optional.of(task.getPayment()));

            service.recover(TASK_ID, NOW);

            InOrder inOrder = inOrder(paymentRepository, handler);
            inOrder.verify(paymentRepository).findByIdWithPessimisticWriteLock(2L);
            inOrder.verify(handler).recover(task);
        }

        @Test
        @DisplayName("부분 취소 복구가 최대 재시도 횟수에 도달하면 Handler에 최종 실패 처리를 요청한다.")
        void delegatesFinalFailureAfterMaximumPartialCancelRetries() {
            PaymentRecoveryTask task = partialCancelTask();
            for (int attempt = 0; attempt < 5; attempt++) {
                task.startProcessing(NOW.minusMinutes(2));
                task.scheduleRetry("Toss 부분 취소 실패", NOW.minusMinutes(1));
            }
            PaymentRecoveryHandler handler = mock(PaymentRecoveryHandler.class);
            when(handler.getType()).thenReturn(PaymentRecoveryType.PARTIAL_CANCEL);
            when(handler.recover(task)).thenReturn(PaymentRecoveryResult.retry("Toss 부분 취소 실패"));
            PaymentRecoveryService service
                = new PaymentRecoveryService(
                    paymentRecoveryTaskRepository,
                    paymentRepository,
                    transactionTemplate,
                    List.of(handler)
                );
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID)).thenReturn(Optional.of(task));
            when(paymentRepository.findByIdWithPessimisticWriteLock(2L)).thenReturn(Optional.of(task.getPayment()));

            service.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.FAILED);
            verify(handler).handleFinalFailure(task, "Toss 부분 취소 실패", NOW);
        }

        @Test
        @DisplayName("로컬 상태 반영 중 예외가 발생하면 별도 트랜잭션에서 다음 복구를 예약한다.")
        void recordsRetryInSeparateTransactionAfterUnexpectedFailure() {
            PaymentRecoveryTask processingTask = partialCancelTask();
            // 첫 트랜잭션이 롤백되면 재조회한 작업은 기존 PENDING 상태로 복원된다.
            PaymentRecoveryTask rolledBackTask = partialCancelTask();
            PaymentRecoveryHandler handler = mock(PaymentRecoveryHandler.class);
            when(handler.getType()).thenReturn(PaymentRecoveryType.PARTIAL_CANCEL);
            when(handler.recover(processingTask)).thenThrow(new RuntimeException("로컬 상태 반영 실패"));
            PaymentRecoveryService service
                = new PaymentRecoveryService(
                    paymentRecoveryTaskRepository,
                    paymentRepository,
                    transactionTemplate,
                    List.of(handler)
                );
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID))
                .thenReturn(Optional.of(processingTask))
                .thenReturn(Optional.of(rolledBackTask));
            when(paymentRepository.findByIdWithPessimisticWriteLock(2L))
                .thenReturn(Optional.of(processingTask.getPayment()));

            service.recover(TASK_ID, NOW);

            assertThat(rolledBackTask.getStatus()).isEqualTo(PaymentRecoveryStatus.RETRY);
            assertThat(rolledBackTask.getAttemptCount()).isEqualTo(1);
            assertThat(rolledBackTask.getNextRetryAt()).isEqualTo(NOW.plusMinutes(1));
            assertThat(rolledBackTask.getLastError()).isEqualTo("결제 복구 처리 중 예기치 않은 오류가 발생했습니다.");
            verify(paymentRecoveryTaskRepository, times(2)).findByIdWithPessimisticWriteLock(TASK_ID);
            verify(transactionManager, times(2)).getTransaction(any(TransactionDefinition.class));
            // 복구 트랜잭션의 롤벡
            verify(transactionManager).rollback(transactionStatus);
            // 재시도 기록의 커밋
            verify(transactionManager).commit(transactionStatus);
        }

        @Test
        @DisplayName("Toss에서 승인된 결제를 전액 취소하고 복구 작업을 완료한다.")
        void cancelsApprovedPaymentAndCompletesTask() {
            PaymentRecoveryTask task = confirmRecoveryTask();
            TossPaymentResponse paymentResponse = mock(TossPaymentResponse.class);
            TossPaymentResponse cancelResponse = mock(TossPaymentResponse.class);
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID)).thenReturn(Optional.of(task));
            when(tossPaymentClient.getPayment(PAYMENT_KEY)).thenReturn(paymentResponse);
            when(paymentResponse.isApproved()).thenReturn(true);
            when(tossPaymentClient.cancel(PAYMENT_KEY, RECOVERY_CANCEL_REASON)).thenReturn(cancelResponse);
            when(cancelResponse.isCancelCompleted()).thenReturn(true);

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.COMPLETED);
            assertThat(task.getCompletedAt()).isEqualTo(NOW);
            assertThat(task.getAttemptCount()).isZero();
            verify(tossPaymentClient).cancel(PAYMENT_KEY, RECOVERY_CANCEL_REASON);
        }

        @Test
        @DisplayName("Toss에서 승인 실패 상태가 확인되면 환불 없이 복구 작업을 완료한다.")
        void completesTaskForConfirmFailureStatus() {
            PaymentRecoveryTask task = confirmRecoveryTask();
            TossPaymentResponse paymentResponse = mock(TossPaymentResponse.class);
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID)).thenReturn(Optional.of(task));
            when(tossPaymentClient.getPayment(PAYMENT_KEY)).thenReturn(paymentResponse);
            when(paymentResponse.isApproved()).thenReturn(false);
            when(paymentResponse.isConfirmFailureStatus()).thenReturn(true);

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.COMPLETED);
            assertThat(task.getCompletedAt()).isEqualTo(NOW);
            verify(tossPaymentClient, never()).cancel(PAYMENT_KEY, RECOVERY_CANCEL_REASON);
        }

        @Test
        @DisplayName("Toss 결제가 최종 상태가 아니면 다음 복구를 예약한다.")
        void schedulesRetryForNonFinalPaymentStatus() {
            PaymentRecoveryTask task = confirmRecoveryTask();
            TossPaymentResponse paymentResponse = mock(TossPaymentResponse.class);
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID)).thenReturn(Optional.of(task));
            when(tossPaymentClient.getPayment(PAYMENT_KEY)).thenReturn(paymentResponse);
            when(paymentResponse.isApproved()).thenReturn(false);
            when(paymentResponse.isConfirmFailureStatus()).thenReturn(false);
            when(paymentResponse.getStatus()).thenReturn("IN_PROGRESS");

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.RETRY);
            assertThat(task.getAttemptCount()).isEqualTo(1);
            assertThat(task.getNextRetryAt()).isEqualTo(NOW.plusMinutes(1));
            assertThat(task.getLastError()).isEqualTo("토스 결제가 아직 최종 상태가 아닙니다.");
            verify(tossPaymentClient, never()).cancel(PAYMENT_KEY, RECOVERY_CANCEL_REASON);
        }

        @Test
        @DisplayName("자동 환불 완료를 확인할 수 없으면 다음 복구를 예약한다.")
        void schedulesRetryWhenCancelIsNotCompleted() {
            PaymentRecoveryTask task = confirmRecoveryTask();
            TossPaymentResponse paymentResponse = mock(TossPaymentResponse.class);
            TossPaymentResponse cancelResponse = mock(TossPaymentResponse.class);
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID)).thenReturn(Optional.of(task));
            when(tossPaymentClient.getPayment(PAYMENT_KEY)).thenReturn(paymentResponse);
            when(paymentResponse.isApproved()).thenReturn(true);
            when(tossPaymentClient.cancel(PAYMENT_KEY, RECOVERY_CANCEL_REASON)).thenReturn(cancelResponse);
            when(cancelResponse.isCancelCompleted()).thenReturn(false);

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.RETRY);
            assertThat(task.getAttemptCount()).isEqualTo(1);
            assertThat(task.getNextRetryAt()).isEqualTo(NOW.plusMinutes(1));
            assertThat(task.getLastError()).isEqualTo("토스 결제 자동 환불 상태를 확인할 수 없습니다.");
        }

        @Test
        @DisplayName("Toss 조회 중 예외가 발생하면 다음 복구를 예약한다.")
        void schedulesRetryWhenTossRequestFails() {
            PaymentRecoveryTask task = confirmRecoveryTask();
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID)).thenReturn(Optional.of(task));
            when(tossPaymentClient.getPayment(PAYMENT_KEY)).thenThrow(new RuntimeException("Toss 조회 실패"));

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.RETRY);
            assertThat(task.getAttemptCount()).isEqualTo(1);
            assertThat(task.getNextRetryAt()).isEqualTo(NOW.plusMinutes(1));
            assertThat(task.getLastError()).isEqualTo("토스 결제 조회 또는 자동 환불 요청에 실패했습니다.");
        }

        @Test
        @DisplayName("최대 재시도 횟수에 도달하면 복구 작업을 최종 실패 처리한다.")
        void failsTaskAfterMaximumRetries() {
            PaymentRecoveryTask task = confirmRecoveryTask();
            for (int attempt = 0; attempt < 5; attempt++) {
                task.startProcessing(NOW.minusMinutes(2));
                task.scheduleRetry("토스 조회 실패", NOW.minusMinutes(1));
            }
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID)).thenReturn(Optional.of(task));
            when(tossPaymentClient.getPayment(PAYMENT_KEY)).thenThrow(new RuntimeException("Toss 조회 실패"));

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.FAILED);
            assertThat(task.getAttemptCount()).isEqualTo(5);
            assertThat(task.getNextRetryAt()).isNull();
            assertThat(task.getLastError()).isEqualTo("토스 결제 조회 또는 자동 환불 요청에 실패했습니다.");
        }
    }
}
