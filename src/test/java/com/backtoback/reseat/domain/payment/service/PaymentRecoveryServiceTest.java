package com.backtoback.reseat.domain.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryStatus;
import com.backtoback.reseat.domain.payment.entity.PaymentRecoveryTask;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;
import com.backtoback.reseat.domain.payment.repository.PaymentRecoveryTaskRepository;

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
    private TossPaymentClient tossPaymentClient;

    @InjectMocks
    private PaymentRecoveryService paymentRecoveryService;

    private PaymentRecoveryTask pendingTask() {
        Payment payment = mock(Payment.class);
        when(payment.getPgPaymentKey()).thenReturn(PAYMENT_KEY);
        return new PaymentRecoveryTask(payment);
    }

    @Nested
    @DisplayName("승인 상태가 불명확한 결제를 복구한다")
    class Recover {

        @Test
        @DisplayName("복구 작업이 존재하지 않으면 Toss를 호출하지 않는다.")
        void ignoresMissingTask() {
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID))
                .thenReturn(Optional.empty());

            paymentRecoveryService.recover(TASK_ID, NOW);

            verifyNoInteractions(tossPaymentClient);
        }

        @Test
        @DisplayName("이미 완료된 작업은 다시 처리하지 않는다.")
        void ignoresCompletedTask() {
            PaymentRecoveryTask task = new PaymentRecoveryTask(mock(Payment.class));
            task.startProcessing(NOW.minusMinutes(2));
            task.complete(NOW.minusMinutes(1));
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID))
                .thenReturn(Optional.of(task));

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.COMPLETED);
            verifyNoInteractions(tossPaymentClient);
        }

        @Test
        @DisplayName("재시도 시각이 지나지 않은 작업은 처리하지 않는다.")
        void ignoresRetryTaskBeforeDueTime() {
            PaymentRecoveryTask task = new PaymentRecoveryTask(mock(Payment.class));
            task.startProcessing(NOW.minusMinutes(1));
            task.scheduleRetry("토스 조회 실패", NOW.plusMinutes(1));
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID))
                .thenReturn(Optional.of(task));

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.RETRY);
            assertThat(task.getAttemptCount()).isEqualTo(1);
            verifyNoInteractions(tossPaymentClient);
        }

        @Test
        @DisplayName("Toss에서 승인된 결제를 전액 취소하고 복구 작업을 완료한다.")
        void cancelsApprovedPaymentAndCompletesTask() {
            PaymentRecoveryTask task = pendingTask();
            TossPaymentResponse paymentResponse = mock(TossPaymentResponse.class);
            TossPaymentResponse cancelResponse = mock(TossPaymentResponse.class);
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID))
                .thenReturn(Optional.of(task));
            when(tossPaymentClient.getPayment(PAYMENT_KEY)).thenReturn(paymentResponse);
            when(paymentResponse.isApproved()).thenReturn(true);
            when(tossPaymentClient.cancel(PAYMENT_KEY, RECOVERY_CANCEL_REASON))
                .thenReturn(cancelResponse);
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
            PaymentRecoveryTask task = pendingTask();
            TossPaymentResponse paymentResponse = mock(TossPaymentResponse.class);
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID))
                .thenReturn(Optional.of(task));
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
            PaymentRecoveryTask task = pendingTask();
            TossPaymentResponse paymentResponse = mock(TossPaymentResponse.class);
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID))
                .thenReturn(Optional.of(task));
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
            PaymentRecoveryTask task = pendingTask();
            TossPaymentResponse paymentResponse = mock(TossPaymentResponse.class);
            TossPaymentResponse cancelResponse = mock(TossPaymentResponse.class);
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID))
                .thenReturn(Optional.of(task));
            when(tossPaymentClient.getPayment(PAYMENT_KEY)).thenReturn(paymentResponse);
            when(paymentResponse.isApproved()).thenReturn(true);
            when(tossPaymentClient.cancel(PAYMENT_KEY, RECOVERY_CANCEL_REASON))
                .thenReturn(cancelResponse);
            when(cancelResponse.isCancelCompleted()).thenReturn(false);

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.RETRY);
            assertThat(task.getAttemptCount()).isEqualTo(1);
            assertThat(task.getNextRetryAt()).isEqualTo(NOW.plusMinutes(1));
            assertThat(task.getLastError())
                .isEqualTo("토스 결제 자동 환불 상태를 확인할 수 없습니다.");
        }

        @Test
        @DisplayName("Toss 조회 중 예외가 발생하면 다음 복구를 예약한다.")
        void schedulesRetryWhenTossRequestFails() {
            PaymentRecoveryTask task = pendingTask();
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID))
                .thenReturn(Optional.of(task));
            when(tossPaymentClient.getPayment(PAYMENT_KEY))
                .thenThrow(new RuntimeException("Toss 조회 실패"));

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.RETRY);
            assertThat(task.getAttemptCount()).isEqualTo(1);
            assertThat(task.getNextRetryAt()).isEqualTo(NOW.plusMinutes(1));
            assertThat(task.getLastError())
                .isEqualTo("토스 결제 조회 또는 자동 환불 요청에 실패했습니다.");
        }

        @Test
        @DisplayName("최대 재시도 횟수에 도달하면 복구 작업을 최종 실패 처리한다.")
        void failsTaskAfterMaximumRetries() {
            PaymentRecoveryTask task = pendingTask();
            for (int attempt = 0; attempt < 5; attempt++) {
                task.startProcessing(NOW.minusMinutes(2));
                task.scheduleRetry("토스 조회 실패", NOW.minusMinutes(1));
            }
            when(paymentRecoveryTaskRepository.findByIdWithPessimisticWriteLock(TASK_ID))
                .thenReturn(Optional.of(task));
            when(tossPaymentClient.getPayment(PAYMENT_KEY))
                .thenThrow(new RuntimeException("Toss 조회 실패"));

            paymentRecoveryService.recover(TASK_ID, NOW);

            assertThat(task.getStatus()).isEqualTo(PaymentRecoveryStatus.FAILED);
            assertThat(task.getAttemptCount()).isEqualTo(5);
            assertThat(task.getNextRetryAt()).isNull();
            assertThat(task.getLastError())
                .isEqualTo("토스 결제 조회 또는 자동 환불 요청에 실패했습니다.");
        }
    }
}
