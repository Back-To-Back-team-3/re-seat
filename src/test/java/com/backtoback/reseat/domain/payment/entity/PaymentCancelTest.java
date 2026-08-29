package com.backtoback.reseat.domain.payment.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.backtoback.reseat.domain.ticket.entity.Ticket;

@DisplayName("PaymentCancel 단위 테스트")
class PaymentCancelTest {

    private static final String REASON = "사용자 티켓 취소";
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 8, 29, 12, 0);

    private PaymentCancel pendingCancel() {
        return PaymentCancel.create(Payment.builder().build(), mock(Ticket.class), REASON);
    }

    private PaymentCancel cancelInStatus(PaymentCancelStatus status) {
        PaymentCancel paymentCancel = pendingCancel();

        // 필드를 직접 변경하지 않고 실제 상태 전이 메서드로 각 테스트의 시작 상태를 만든다.
        switch (status) {
            case PENDING -> {
                return paymentCancel;
            }
            case DONE -> paymentCancel.complete("transaction-key", BASE_TIME);
            case FAILED -> paymentCancel.fail("PG 취소 실패", BASE_TIME);
        }
        return paymentCancel;
    }

    @Nested
    @DisplayName("결제 취소 이력을 생성한다")
    class Create {

        @Test
        @DisplayName("결제와 티켓 및 취소 사유를 포함한 PENDING 이력을 생성한다.")
        void createsPendingCancel() {
            Payment payment = Payment.builder().build();
            Ticket ticket = mock(Ticket.class);

            PaymentCancel paymentCancel = PaymentCancel.create(payment, ticket, REASON);

            assertThat(paymentCancel.getPayment()).isSameAs(payment);
            assertThat(paymentCancel.getTicket()).isSameAs(ticket);
            assertThat(paymentCancel.getReason()).isEqualTo(REASON);
            assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.PENDING);
            // 생성과 동시에 Payment에서도 같은 취소 이력을 조회할 수 있어야 한다.
            assertThat(payment.getCancels()).containsExactly(paymentCancel);
        }

        @Test
        @DisplayName("필수값이 없으면 결제 취소 이력을 생성할 수 없다.")
        void rejectsMissingRequiredValue() {
            Payment payment = Payment.builder().build();
            Ticket ticket = mock(Ticket.class);

            assertThatThrownBy(() -> PaymentCancel.create(null, ticket, REASON))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> PaymentCancel.create(payment, null, REASON))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> PaymentCancel.create(payment, ticket, " "))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("결제 취소를 완료한다")
    class Complete {

        @Test
        @DisplayName("PENDING 이력을 DONE으로 변경하고 PG 거래 키와 완료 시각을 기록한다.")
        void completesPendingCancel() {
            PaymentCancel paymentCancel = pendingCancel();

            paymentCancel.complete("transaction-key", BASE_TIME);

            assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.DONE);
            assertThat(paymentCancel.getPgTransactionKey()).isEqualTo("transaction-key");
            assertThat(paymentCancel.getCompletedAt()).isEqualTo(BASE_TIME);
        }

        @ParameterizedTest(name = "{0} 상태의 취소 이력은 완료할 수 없다")
        @EnumSource(
            value = PaymentCancelStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "PENDING"
        )
        @DisplayName("PENDING이 아닌 이력을 완료하면 예외가 발생한다.")
        void requiresPendingStatus(PaymentCancelStatus status) {
            PaymentCancel paymentCancel = cancelInStatus(status);

            assertThatThrownBy(() -> paymentCancel.complete("transaction-key", BASE_TIME.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
            assertThat(paymentCancel.getStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("PG 거래 키 또는 완료 시각이 없으면 완료할 수 없다.")
        void rejectsMissingRequiredValue() {
            PaymentCancel paymentCancel = pendingCancel();

            assertThatThrownBy(() -> paymentCancel.complete(null, BASE_TIME))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> paymentCancel.complete(" ", BASE_TIME))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> paymentCancel.complete("transaction-key", null))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("결제 취소를 실패 처리한다")
    class Fail {

        @Test
        @DisplayName("PENDING 이력을 FAILED로 변경하고 실패 사유와 시각을 기록한다.")
        void failsPendingCancel() {
            PaymentCancel paymentCancel = pendingCancel();

            paymentCancel.fail("PG 취소 실패", BASE_TIME);

            assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.FAILED);
            assertThat(paymentCancel.getFailureReason()).isEqualTo("PG 취소 실패");
            assertThat(paymentCancel.getFailedAt()).isEqualTo(BASE_TIME);
        }

        @ParameterizedTest(name = "{0} 상태의 취소 이력은 실패 처리할 수 없다")
        @EnumSource(
            value = PaymentCancelStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "PENDING"
        )
        @DisplayName("PENDING이 아닌 이력을 실패 처리하면 예외가 발생한다.")
        void requiresPendingStatus(PaymentCancelStatus status) {
            PaymentCancel paymentCancel = cancelInStatus(status);

            assertThatThrownBy(() -> paymentCancel.fail("PG 취소 실패", BASE_TIME.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
            assertThat(paymentCancel.getStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("실패 사유 또는 실패 시각이 없으면 실패 처리할 수 없다.")
        void rejectsMissingRequiredValue() {
            PaymentCancel paymentCancel = pendingCancel();

            assertThatThrownBy(() -> paymentCancel.fail(null, BASE_TIME)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> paymentCancel.fail(" ", BASE_TIME)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> paymentCancel.fail("PG 취소 실패", null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("실패한 결제 취소를 재시도한다")
    class Retry {

        @Test
        @DisplayName("FAILED 이력을 PENDING으로 되돌리고 이전 실패 정보를 정리한다.")
        void retriesFailedCancel() {
            PaymentCancel paymentCancel = pendingCancel();

            // 새 이력을 만들지 않고 실패한 이력에 실패 결과를 남긴 뒤 재사용한다.
            paymentCancel.fail("PG 취소 실패", BASE_TIME);

            paymentCancel.retry("관리자 재시도");

            assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.PENDING);
            assertThat(paymentCancel.getReason()).isEqualTo("관리자 재시도");
            assertThat(paymentCancel.getFailureReason()).isNull();
            assertThat(paymentCancel.getFailedAt()).isNull();
        }

        @ParameterizedTest(name = "{0} 상태의 취소 이력은 재시도할 수 없다")
        @EnumSource(
            value = PaymentCancelStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "FAILED"
        )
        @DisplayName("FAILED가 아닌 이력을 재시도하면 예외가 발생한다.")
        void requiresFailedStatus(PaymentCancelStatus status) {
            PaymentCancel paymentCancel = cancelInStatus(status);

            assertThatThrownBy(() -> paymentCancel.retry("관리자 재시도")).isInstanceOf(IllegalStateException.class);
            assertThat(paymentCancel.getStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("새로운 취소 사유가 없으면 재시도할 수 없다.")
        void rejectsMissingReason() {
            PaymentCancel paymentCancel = cancelInStatus(PaymentCancelStatus.FAILED);

            assertThatThrownBy(() -> paymentCancel.retry(" ")).isInstanceOf(IllegalArgumentException.class);
            assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("PG 취소 완료 여부를 확인한다")
    class IsDone {

        @ParameterizedTest(name = "{0} 상태의 완료 여부를 반환한다")
        @EnumSource(PaymentCancelStatus.class)
        @DisplayName("DONE 상태인 경우에만 true를 반환한다.")
        void returnsWhetherCancelIsDone(PaymentCancelStatus status) {
            PaymentCancel paymentCancel = cancelInStatus(status);

            assertThat(paymentCancel.isDone()).isEqualTo(status == PaymentCancelStatus.DONE);
        }
    }
}
