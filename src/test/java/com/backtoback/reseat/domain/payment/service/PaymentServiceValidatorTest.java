package com.backtoback.reseat.domain.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyConflictException;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyRequiredException;
import com.backtoback.reseat.domain.payment.exception.IdempotencyKeyUnavailableException;
import com.backtoback.reseat.domain.payment.exception.PaymentAccessDeniedException;
import com.backtoback.reseat.domain.payment.exception.PaymentAlreadyFinalizedException;
import com.backtoback.reseat.domain.payment.exception.PaymentCallbackMismatchException;
import com.backtoback.reseat.domain.payment.exception.PaymentCancelNotAllowedException;
import com.backtoback.reseat.domain.payment.exception.PaymentPgKeyMissingException;
import com.backtoback.reseat.global.exception.ErrorCode;
import com.backtoback.reseat.domain.user.entity.User;

@DisplayName("PaymentServiceValidator 결제 검증")
class PaymentServiceValidatorTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final String PG_ORDER_ID = "ORD-20260727-000001";
    private static final String PG_PAYMENT_KEY = "payment-key";
    private static final int AMOUNT = 10000;

    private final PaymentServiceValidator validator = new PaymentServiceValidator();

    private Payment payment(PaymentStatus status, String pgPaymentKey) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(ORDER_ID);

        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);

        return Payment
            .builder()
            .paymentNo("PAY-20260727010000-000001")
            .order(order)
            .user(user)
            .amount(AMOUNT)
            .idempotencyKey(IDEMPOTENCY_KEY)
            .status(status)
            .pgProvider(PgProvider.TOSS)
            .pgOrderId(PG_ORDER_ID)
            .pgPaymentKey(pgPaymentKey)
            .build();
    }

    private Payment readyPayment() {
        return payment(PaymentStatus.READY, null);
    }

    @Nested
    @DisplayName("결제 요청의 멱등키를 검증한다")
    class ValidateIdempotencyKey {

        @Test
        @DisplayName("값이 있는 멱등키는 사용할 수 있다.")
        void acceptsPresentKey() {
            assertThatCode(() -> validator.validateIdempotencyKey(IDEMPOTENCY_KEY)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 또는 공백 멱등키는 필수 값 예외가 발생한다.")
        void rejectsMissingKey() {
            assertThatThrownBy(() -> validator.validateIdempotencyKey(null))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
            assertThatThrownBy(() -> validator.validateIdempotencyKey(" "))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
        }
    }

    @Nested
    @DisplayName("기존 멱등키 요청의 주문을 검증한다")
    class ValidateIdempotencyRequest {

        @Test
        @DisplayName("기존 멱등키 결제와 주문이 같으면 통과한다.")
        void acceptsSameOrder() {
            Payment payment = readyPayment();

            assertThatCode(() -> validator.validateIdempotencyRequest(payment, ORDER_ID)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("기존 멱등키 결제와 주문이 다르면 충돌 예외가 발생한다.")
        void rejectsDifferentOrder() {
            Payment payment = readyPayment();

            assertThatThrownBy(() -> validator.validateIdempotencyRequest(payment, 999L))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        }
    }

    @Nested
    @DisplayName("현재 결제 시도의 활성 멱등키를 검증한다")
    class ValidateActiveIdempotencyKey {

        @Test
        @DisplayName("현재 활성 멱등키와 같으면 통과한다.")
        void acceptsActiveKey() {
            Payment payment = readyPayment();

            assertThatCode(() -> validator.validateActiveIdempotencyKey(payment, IDEMPOTENCY_KEY))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("현재 활성 멱등키와 다르면 사용할 수 없는 키 예외가 발생한다.")
        void rejectsInactiveKey() {
            Payment payment = readyPayment();

            assertThatThrownBy(() -> validator.validateActiveIdempotencyKey(payment, "inactive-key"))
                .isInstanceOf(IdempotencyKeyUnavailableException.class);
        }
    }

    @Nested
    @DisplayName("결제 소유자를 검증한다")
    class ValidateOwner {

        @Test
        @DisplayName("결제 소유자와 요청 사용자가 같으면 통과한다.")
        void acceptsOwner() {
            Payment payment = readyPayment();

            assertThatCode(() -> validator.validateOwner(payment, USER_ID)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("결제 소유자와 요청 사용자가 다르면 접근 거부 예외가 발생한다.")
        void rejectsOtherUser() {
            Payment payment = readyPayment();

            assertThatThrownBy(() -> validator.validateOwner(payment, 999L))
                .isInstanceOf(PaymentAccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("결제 승인 가능 여부를 검증한다")
    class ValidateConfirmable {

        @Test
        @DisplayName("READY 결제의 주문 번호와 금액이 일치하면 승인할 수 있다.")
        void acceptsMatchingReadyPayment() {
            Payment payment = readyPayment();

            assertThatCode(() -> validator.validateConfirmable(payment, PG_ORDER_ID, AMOUNT))
                .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0} 상태의 결제는 승인할 수 없다")
        @EnumSource(
            value = PaymentStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "READY"
        )
        @DisplayName("READY가 아닌 모든 결제는 이미 처리된 결제 예외가 발생한다.")
        void rejectsFinalizedPayment(PaymentStatus status) {
            Payment payment = payment(status, PG_PAYMENT_KEY);

            assertThatThrownBy(() -> validator.validateConfirmable(payment, PG_ORDER_ID, AMOUNT))
                .isInstanceOf(PaymentAlreadyFinalizedException.class);
        }
    }

    @Nested
    @DisplayName("결제 실패 처리 가능 여부를 검증한다")
    class ValidateFailable {

        @Test
        @DisplayName("READY 결제는 실패 처리할 수 있다.")
        void acceptsReadyPayment() {
            Payment payment = readyPayment();

            assertThatCode(() -> validator.validateFailable(payment)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0} 상태의 결제는 실패 처리할 수 없다")
        @EnumSource(
            value = PaymentStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "READY"
        )
        @DisplayName("READY가 아닌 모든 결제는 이미 처리된 결제 예외가 발생한다.")
        void rejectsFinalizedPayment(PaymentStatus status) {
            Payment payment = payment(status, PG_PAYMENT_KEY);

            assertThatThrownBy(() -> validator.validateFailable(payment))
                .isInstanceOf(PaymentAlreadyFinalizedException.class);
        }
    }

    @Nested
    @DisplayName("결제 취소 가능 여부를 검증한다")
    class ValidateCancelable {

        @Test
        @DisplayName("PG 결제 키가 있는 APPROVED 결제는 취소할 수 있다.")
        void acceptsApprovedPaymentWithPgKey() {
            Payment payment = payment(PaymentStatus.APPROVED, PG_PAYMENT_KEY);

            assertThatCode(() -> validator.validateCancelable(payment)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0} 상태의 결제는 취소할 수 없다")
        @EnumSource(
            value = PaymentStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "APPROVED"
        )
        @DisplayName("APPROVED가 아닌 모든 결제는 취소할 수 없다.")
        void rejectsUnapprovedPayment(PaymentStatus status) {
            Payment payment = payment(status, PG_PAYMENT_KEY);

            assertThatThrownBy(() -> validator.validateCancelable(payment))
                .isInstanceOf(PaymentCancelNotAllowedException.class);
        }

        @Test
        @DisplayName("PG 결제 키가 없는 APPROVED 결제는 취소할 수 없다.")
        void rejectsPaymentWithoutPgKey() {
            Payment payment = payment(PaymentStatus.APPROVED, null);

            assertThatThrownBy(() -> validator.validateCancelable(payment))
                .isInstanceOf(PaymentPgKeyMissingException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_PG_KEY_MISSING);
        }
    }

    @Nested
    @DisplayName("PG 주문 번호의 일치 여부를 검증한다")
    class ValidatePgOrderId {

        @Test
        @DisplayName("PG 주문 번호가 일치하면 통과한다.")
        void acceptsMatchingPgOrderId() {
            Payment payment = readyPayment();

            assertThatCode(() -> validator.validatePgOrderId(payment, PG_ORDER_ID)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PG 주문 번호가 다르면 콜백 정보 불일치 예외가 발생한다.")
        void rejectsDifferentPgOrderId() {
            Payment payment = readyPayment();

            assertThatThrownBy(() -> validator.validatePgOrderId(payment, "different-order"))
                .isInstanceOf(PaymentCallbackMismatchException.class);
        }
    }

    @Nested
    @DisplayName("결제 금액의 일치 여부를 검증한다")
    class ValidateAmount {

        @Test
        @DisplayName("결제 금액이 일치하면 통과한다.")
        void acceptsMatchingAmount() {
            Payment payment = readyPayment();

            assertThatCode(() -> validator.validateAmount(payment, AMOUNT)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("결제 금액이 다르면 콜백 정보 불일치 예외가 발생한다.")
        void rejectsDifferentAmount() {
            Payment payment = readyPayment();

            assertThatThrownBy(() -> validator.validateAmount(payment, AMOUNT + 1))
                .isInstanceOf(PaymentCallbackMismatchException.class);
        }
    }
}
