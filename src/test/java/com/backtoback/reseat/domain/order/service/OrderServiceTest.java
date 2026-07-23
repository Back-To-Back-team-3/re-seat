package com.backtoback.reseat.domain.order.service;

import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.exception.OrderNotFoundException;
import com.backtoback.reseat.domain.order.repository.OrderItemRepository;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 주문 상태 전이")
public class OrderServiceTest {

    private static final String ORDER_NO = "ORD-TEST-000001";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 24, 2, 0);
    private static final LocalDateTime PAYMENT_DEADLINE = CREATED_AT.plusMinutes(8);
    private static final int TOTAL_AMOUNT = 34_000;
    private static final Long ORDER_ID = 1L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationSeatRepository reservationSeatRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderService orderService;

    private Order createdOrder() {
        return Order.of(
                ORDER_NO,
                mock(User.class),
                mock(Reservation.class),
                TOTAL_AMOUNT,
                PAYMENT_DEADLINE
        );
    }

    @Test
    @DisplayName("CREATED 주문을 결제 완료 처리하면 PAID 상태로 변경된다.")
    void completeOrder_changesStatusToPaid() {

        Order order = createdOrder();

        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        orderService.completeOrder(ORDER_ID);

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.PAID);

        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    @DisplayName("CREATED 주문을 결제 기한 만료 처리하면 EXPIRED 상태로 변경된다.")
    void expireOrder_changesStatusToExpired() {

        Order order = createdOrder();

        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        orderService.expireOrder(ORDER_ID);

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.EXPIRED);

        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    @DisplayName("CREATED 주문을 종결 결제 실패 처리하면 CANCELED 상태로 변경된다.")
    void failOrder_changesStatusToCanceled() {

        Order order = createdOrder();

        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(order));

        orderService.failOrder(ORDER_ID);

        assertThat(order.getStatus())
                .isEqualTo(OrderStatus.CANCELED);

        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    @DisplayName("존재하지 않는 주문을 결제 완료 처리하면 예외가 발생한다.")
    void completeOrder_throwsExceptionWhenOrderNotFound() {

        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.completeOrder(ORDER_ID))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository).findById(ORDER_ID);
    }
}
