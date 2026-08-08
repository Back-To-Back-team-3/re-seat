package com.backtoback.reseat.domain.payment.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.payment.exception.PaymentAlreadyFinalizedException;
import com.backtoback.reseat.domain.payment.exception.PaymentCallbackMismatchException;
import com.backtoback.reseat.domain.payment.exception.PaymentCancelNotAllowedException;
import com.backtoback.reseat.domain.user.entity.User;

@DisplayName("Payment 상태 전이")
class PaymentTest {

    private static final String PAYMENT_NO = "PAY-20260725030500-000001";
    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final String PG_ORDER_ID = "ORD-20260725-000001";
    private static final int AMOUNT = 10000;

    private Payment payment(PaymentStatus status) {
        return Payment.builder()
            .paymentNo(PAYMENT_NO)
            .order(mock(Order.class))
            .user(mock(User.class))
            .amount(AMOUNT)
            .idempotencyKey(IDEMPOTENCY_KEY)
            .status(status)
            .pgProvider(PgProvider.TOSS)
            .pgOrderId(PG_ORDER_ID)
            .build();
    }

    private Payment readyPayment() {
        return payment(PaymentStatus.READY);
    }

    @Nested
    @DisplayName("결제를 생성한다")
    class Creation {

        @Test
        @DisplayName("기본 상태는 READY이고 기본 PG사는 MOCK이다.")
        void defaultsToReadyAndMockProvider() {
            Payment payment = Payment.builder().build();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
            assertThat(payment.getPgProvider()).isEqualTo(PgProvider.MOCK);
        }
    }

    @Nested
    @DisplayName("PG 결제 키를 연결한다")
    class AssignPgPaymentKey {

        @Test
        @DisplayName("PG 결제 키를 처음 연결하면 결제에 저장된다.")
        void savesKey() {
            Payment payment = readyPayment();

            payment.assignPgPaymentKey("payment-key");

            assertThat(payment.getPgPaymentKey()).isEqualTo("payment-key");
        }

        @Test
        @DisplayName("동일한 PG 결제 키를 다시 연결해도 기존 키가 유지된다.")
        void allowsSameKey() {
            Payment payment = readyPayment();
            payment.assignPgPaymentKey("payment-key");

            payment.assignPgPaymentKey("payment-key");

            assertThat(payment.getPgPaymentKey()).isEqualTo("payment-key");
        }

        @Test
        @DisplayName("다른 PG 결제 키를 다시 연결하면 결제 정보 불일치 예외가 발생한다.")
        void rejectsDifferentKey() {
            Payment payment = readyPayment();
            payment.assignPgPaymentKey("payment-key");

            assertThatThrownBy(() -> payment.assignPgPaymentKey("different-payment-key"))
                .isInstanceOf(PaymentCallbackMismatchException.class);
            assertThat(payment.getPgPaymentKey()).isEqualTo("payment-key");
        }
    }

    @Nested
    @DisplayName("결제를 승인한다")
    class Approve {

        @Test
        @DisplayName("승인하면 APPROVED 상태와 결제 수단 및 승인 시각이 기록된다.")
        void marksApproved() {
            Payment payment = readyPayment();
            LocalDateTime approvedAt = LocalDateTime.of(2026, 7, 25, 12, 0);

            payment.approve("CARD", approvedAt);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.getMethod()).isEqualTo("CARD");
            assertThat(payment.getApprovedAt()).isEqualTo(approvedAt);
            assertThat(payment.getFailReason()).isNull();
            assertThat(payment.getFailedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("결제를 실패 처리한다")
    class Fail {

        @Test
        @DisplayName("실패 처리하면 FAILED 상태와 실패 사유 및 시각이 기록된다.")
        void marksFailed() {
            Payment payment = readyPayment();
            LocalDateTime failedAt = LocalDateTime.of(2026, 7, 25, 12, 0);

            payment.fail("결제 승인 실패", failedAt);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailReason()).isEqualTo("결제 승인 실패");
            assertThat(payment.getFailedAt()).isEqualTo(failedAt);
        }
    }

    @Nested
    @DisplayName("결제를 취소한다")
    class Cancel {

        @Test
        @DisplayName("승인된 결제를 취소하면 CANCELED 상태가 된다.")
        void marksCanceled() {
            Payment payment = readyPayment();
            payment.approve("CARD", LocalDateTime.of(2026, 7, 25, 12, 0));

            payment.cancel();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        }

        @ParameterizedTest(name = "{0} 상태의 결제는 취소할 수 없다")
        @EnumSource(
            value = PaymentStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "APPROVED"
        )
        @DisplayName("APPROVED가 아닌 모든 결제는 취소 불가 예외가 발생한다.")
        void rejectsUnapprovedPayment(PaymentStatus status) {
            Payment payment = payment(status);

            assertThatThrownBy(payment::cancel)
                .isInstanceOf(PaymentCancelNotAllowedException.class);
            assertThat(payment.getStatus()).isEqualTo(status);
        }
    }

    @Nested
    @DisplayName("결제 시도의 멱등키를 교체한다")
    class ChangeIdempotencyKey {

        @Test
        @DisplayName("READY 결제는 새로운 멱등키로 교체할 수 있다.")
        void updatesKey() {
            Payment payment = readyPayment();

            payment.changeIdempotencyKey("new-idempotency-key");

            assertThat(payment.getIdempotencyKey()).isEqualTo("new-idempotency-key");
        }

        @ParameterizedTest(name = "{0} 상태의 결제는 멱등키를 교체할 수 없다")
        @EnumSource(
            value = PaymentStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "READY"
        )
        @DisplayName("READY가 아닌 모든 결제는 이미 처리된 결제 예외가 발생한다.")
        void rejectsFinalizedPayment(PaymentStatus status) {
            Payment payment = payment(status);

            assertThatThrownBy(() -> payment.changeIdempotencyKey("new-idempotency-key"))
                .isInstanceOf(PaymentAlreadyFinalizedException.class);
            assertThat(payment.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        }
    }
}
