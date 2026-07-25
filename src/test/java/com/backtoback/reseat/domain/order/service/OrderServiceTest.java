package com.backtoback.reseat.domain.order.service;

import com.backtoback.reseat.domain.order.dto.response.OrderResponse;
import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.exception.OrderNotFoundException;
import com.backtoback.reseat.domain.order.repository.OrderItemRepository;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.order.repository.OrderReservationRepository;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.exception.PreReservationExpiredException;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
public class OrderServiceTest {

    private static final String ORDER_NO = "ORD-TEST-000001";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 24, 2, 0);
    private static final LocalDateTime PAYMENT_DEADLINE = CREATED_AT.plusMinutes(8);
    private static final int TOTAL_AMOUNT = 34_000;
    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long RESERVATION_ID = 1L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderReservationRepository orderReservationRepository;

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

    private record CreateOrderFixture(
            List<GameSeat> gameSeats
    ) {

    }

    private void givenLockedReservation(
            LocalDateTime createdAt,
            LocalDateTime holdExpiresAt
    ) {

        User user = User.builder()
                .id(USER_ID)
                .build();

        Reservation reservation = Reservation.builder()
                .user(user)
                .status(ReservationStatus.HOLDING)
                .holdExpiresAt(holdExpiresAt)
                .build();

        // 생성 시간을 테스트 조건에 맞게 설정한다.
        ReflectionTestUtils.setField(reservation, "createdAt", createdAt);

        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(user));
        given(orderReservationRepository.findByIdWithPessimisticWriteLock(RESERVATION_ID))
                .willReturn(Optional.of(reservation));
    }

    private CreateOrderFixture givenValidCreateOrder(
            LocalDateTime createdAt,
            LocalDateTime holdExpiresAt
    ) {

        return givenValidCreateOrder(createdAt, holdExpiresAt, 1);
    }

    private CreateOrderFixture givenValidCreateOrder(
            LocalDateTime createdAt,
            LocalDateTime holdExpiresAt,
            int seatCount
    ) {

        givenLockedReservation(createdAt, holdExpiresAt);

        List<GameSeat> gameSeats = new ArrayList<>();
        List<ReservationSeat> reservationSeats = new ArrayList<>();

        for (int i = 0; i < seatCount; i++) {
            GameSeat gameSeat = GameSeat.builder()
                    .status(GameSeatStatus.HELD)
                    .build();

            gameSeats.add(gameSeat);

            ReservationSeat reservationSeat = ReservationSeat.builder()
                    .gameSeat(gameSeat)
                    .build();

            reservationSeats.add(reservationSeat);
        }

        given(reservationSeatRepository.findByReservation_Id(RESERVATION_ID))
                .willReturn(reservationSeats);

        given(orderRepository.save(any(Order.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        return new CreateOrderFixture(gameSeats);
    }

    // ---------- 주문 상태 전이 ----------

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

    // ---------- 주문 생성 시 선점 만료 시간 연장 ----------

    @Test
    @DisplayName("선점 만료 시간이 결제 기한보다 빠르면 연장된다.")
    void createOrder_extendsHoldExpiresAt() {

        // given
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime holdExpiresAt = createdAt.plusMinutes(5);

        givenValidCreateOrder(createdAt, holdExpiresAt);

        // when
        OrderResponse response = orderService.createOrder(USER_ID, RESERVATION_ID);

        // then
        assertThat(response.getHoldExpiresAt())
                .isEqualTo(response.getPaymentDeadline());
    }

    @Test
    @DisplayName("선점 만료 시간이 더 늦으면 기존 시간을 유지한다.")
    void createOrder_keepsLaterHoldExpiresAt() {

        // given
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime holdExpiresAt = createdAt.plusMinutes(10);

        givenValidCreateOrder(createdAt, holdExpiresAt);

        // when
        OrderResponse response = orderService.createOrder(USER_ID, RESERVATION_ID);

        // then
        assertThat(response.getHoldExpiresAt())
                .isEqualTo(holdExpiresAt);
    }

    @Test
    @DisplayName("선점 만료 시간은 최초 선점 후 18분을 넘지 않는다.")
    void createOrder_limitsHoldExpiresAt() {

        // given
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(10);
        LocalDateTime holdExpiresAt = createdAt.plusMinutes(17);
        LocalDateTime expectedExpiresAt = createdAt.plusMinutes(18);

        givenValidCreateOrder(createdAt, holdExpiresAt);

        // when
        OrderResponse response = orderService.createOrder(USER_ID, RESERVATION_ID);

        // then
        assertThat(response.getHoldExpiresAt())
                .isEqualTo(expectedExpiresAt);
    }

    @Test
    @DisplayName("조회한 예약이 만료됐으면 주문 생성에 실패한다.")
    void createOrder_throwsExceptionWhenReservationExpired() {

        // given
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime holdExpiresAt = createdAt.minusMinutes(1);

        givenLockedReservation(createdAt, holdExpiresAt);

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(USER_ID, RESERVATION_ID))
                .isInstanceOf(PreReservationExpiredException.class);

        // then
        verifyNoInteractions(reservationSeatRepository);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(orderItemRepository);
        verify(orderReservationRepository, never())
                .updateHoldExpiresAtById(eq(RESERVATION_ID), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("예약과 모든 좌석에 같은 선점 만료 시간이 적용된다.")
    void createOrder_appliesSameHoldExpiresAt() {

        // given
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime holdExpiresAt = createdAt.plusMinutes(5);

        CreateOrderFixture createOrderFixture = givenValidCreateOrder(createdAt, holdExpiresAt, 3);
        List<GameSeat> gameSeats = createOrderFixture.gameSeats();

        // when
        OrderResponse response = orderService.createOrder(USER_ID, RESERVATION_ID);

        // then
        verify(orderReservationRepository).updateHoldExpiresAtById(RESERVATION_ID, response.getHoldExpiresAt());

        assertThat(gameSeats)
                .allSatisfy(gameSeat ->
                        assertThat(gameSeat.getHoldExpiresAt())
                                .isEqualTo(response.getHoldExpiresAt())
                );
    }
}
