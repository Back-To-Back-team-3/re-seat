package com.backtoback.reseat.domain.payment.service;

import static org.assertj.core.api.Assertions.*;
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
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.exception.OrderExpiredException;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.exception.PaymentAccessDeniedException;
import com.backtoback.reseat.domain.payment.exception.PaymentOrderNotFoundException;
import com.backtoback.reseat.domain.payment.exception.PaymentOrderNotPayableException;
import com.backtoback.reseat.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentOrderPolicy 결제·주문 검증 정책")
class PaymentOrderPolicyTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 10L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentOrderPolicy paymentOrderPolicy;

    @Nested
    @DisplayName("주문을 조회하고 결제 요청 사용자에게 소유권이 있는지 검증한다")
    class GetOwnedOrder {

        @Test
        @DisplayName("사용자가 소유한 주문을 반환한다.")
        void returnsOwnedOrder() {
            Order order = mock(Order.class);
            User user = mock(User.class);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(order.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(USER_ID);

            Order result = paymentOrderPolicy.getOwnedOrder(USER_ID, ORDER_ID);

            assertThat(result).isSameAs(order);
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 결제 주문 조회 예외가 발생한다.")
        void rejectsMissingOrder() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentOrderPolicy.getOwnedOrder(USER_ID, ORDER_ID))
                .isInstanceOf(PaymentOrderNotFoundException.class);
        }

        @Test
        @DisplayName("타인의 주문이면 결제 접근을 거부한다.")
        void rejectsOrderOwnedByAnotherUser() {
            Order order = mock(Order.class);
            User user = mock(User.class);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(order.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(2L);

            assertThatThrownBy(() -> paymentOrderPolicy.getOwnedOrder(USER_ID, ORDER_ID))
                .isInstanceOf(PaymentAccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("주문 결제 기한과 상태를 검증한다")
    class EnsurePayable {

        @Test
        @DisplayName("결제 기한이 남은 CREATED 주문은 결제할 수 있다.")
        void acceptsCreatedOrderBeforeDeadline() {
            Order order = mock(Order.class);
            when(order.getPaymentDeadline()).thenReturn(LocalDateTime.now().plusDays(1));
            when(order.getStatus()).thenReturn(OrderStatus.CREATED);

            assertThatCode(() -> paymentOrderPolicy.ensurePayable(null, order)).doesNotThrowAnyException();

            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("결제 기한이 지나면 기존 결제와 주문을 만료 처리한다.")
        void expiresExistingPaymentAndOrderAfterDeadline() {
            Order order = mock(Order.class);
            Payment payment = Payment.builder().order(order).status(PaymentStatus.READY).build();
            when(order.getPaymentDeadline()).thenReturn(LocalDateTime.now().minusDays(1));
            when(order.getId()).thenReturn(ORDER_ID);

            assertThatThrownBy(() -> paymentOrderPolicy.ensurePayable(payment, order))
                .isInstanceOf(OrderExpiredException.class);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getFailReason()).isEqualTo("주문 결제 기한이 만료되었습니다.");
            assertThat(payment.getFailedAt()).isNotNull();
            verify(orderService).expireOrder(ORDER_ID);
        }

        @Test
        @DisplayName("결제 생성 전 기한이 지나면 주문만 만료 처리한다.")
        void expiresOrderWithoutPaymentAfterDeadline() {
            Order order = mock(Order.class);
            when(order.getPaymentDeadline()).thenReturn(LocalDateTime.now().minusDays(1));
            when(order.getId()).thenReturn(ORDER_ID);

            assertThatThrownBy(() -> paymentOrderPolicy.ensurePayable(null, order))
                .isInstanceOf(OrderExpiredException.class);

            verify(orderService).expireOrder(ORDER_ID);
        }

        @ParameterizedTest(name = "{0} 주문은 결제할 수 없다")
        @EnumSource(
            value = OrderStatus.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = "CREATED"
        )
        @DisplayName("결제 기한이 남아도 CREATED가 아닌 주문은 결제할 수 없다.")
        void rejectsNonCreatedOrder(OrderStatus status) {
            Order order = mock(Order.class);
            when(order.getPaymentDeadline()).thenReturn(LocalDateTime.now().plusDays(1));
            when(order.getStatus()).thenReturn(status);

            assertThatThrownBy(() -> paymentOrderPolicy.ensurePayable(null, order))
                .isInstanceOf(PaymentOrderNotPayableException.class);

            verify(orderService, never()).expireOrder(ORDER_ID);
        }
    }
}
