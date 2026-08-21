package com.backtoback.reseat.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.verifyNoInteractions;
import static org.mockito.BDDMockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.backtoback.reseat.domain.order.dto.response.OrderResponse;
import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.exception.InvalidOrderStatusException;
import com.backtoback.reseat.domain.order.exception.OrderExpiredException;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
public class OrderServiceTest {

    private static final String ORDER_NO = "ORD-TEST-000001";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 24, 2, 0);
    private static final LocalDateTime PAYMENT_DEADLINE = CREATED_AT.plusMinutes(8);
    private static final int TOTAL_AMOUNT = 34_000;
    private static final int PRICE = 17_000;
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

    private Order createdOrder(Reservation reservation, LocalDateTime paymentDeadline) {
        return Order.of(ORDER_NO, mock(User.class), reservation, TOTAL_AMOUNT, paymentDeadline);
    }

    private void givenLockedReservation(LocalDateTime createdAt, LocalDateTime holdExpiresAt) {

        User user = User.builder().id(USER_ID).build();

        Reservation reservation
            = Reservation.builder().user(user).status(ReservationStatus.HOLDING).holdExpiresAt(holdExpiresAt).build();

        // 생성 시간을 테스트 조건에 맞게 설정한다.
        ReflectionTestUtils.setField(reservation, "createdAt", createdAt);

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(orderReservationRepository.findByIdWithPessimisticWriteLock(RESERVATION_ID))
            .willReturn(Optional.of(reservation));
    }

    private record CreateOrderFixture(List<GameSeat> gameSeats) {
    }

    private void givenValidCreateOrder(LocalDateTime createdAt, LocalDateTime holdExpiresAt) {

        givenValidCreateOrder(createdAt, holdExpiresAt, 1);
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
            GameSeat gameSeat = GameSeat.builder().status(GameSeatStatus.HELD).build();

            gameSeats.add(gameSeat);

            ReservationSeat reservationSeat = ReservationSeat.builder().gameSeat(gameSeat).build();

            reservationSeats.add(reservationSeat);
        }

        given(reservationSeatRepository.findByReservation_Id(RESERVATION_ID)).willReturn(reservationSeats);

        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        return new CreateOrderFixture(gameSeats);
    }

    /**
     * 결제 완료 상태 전이를 검증할 주문, 예약, 좌석을 함께 전달한다.
     *
     * @param order 결제 완료 상태를 검증할 주문
     * @param reservation 결제 완료 후 CONFIRMED 상태를 검증할 예약
     * @param gameSeat 결제 완료 후 SOLD 상태를 검증할 경기 좌석
     * @param orderItems 결제 완료 성공 시 조회할 주문 항목 목록
     */
    private record CompleteOrderFixture(
        Order order,
        Reservation reservation,
        GameSeat gameSeat,
        List<OrderItem> orderItems
    ) {
    }

    /**
     * 결제 완료 조건부 UPDATE 결과에 따른 주문, 예약, 좌석과 Repository 응답을 준비한다.
     *
     * @param completeOrderCount 결제 완료 조건부 UPDATE가 반환할 변경 주문 수
     * @return 결제 완료 후 상태를 검증할 주문, 예약, 좌석과 주문 항목 fixture
     */
    private CompleteOrderFixture givenCompletableOrder(int completeOrderCount) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime paymentDeadline = now.plusMinutes(8);
        LocalDateTime holdExpiresAt = now.plusMinutes(10);

        Reservation reservation
            = Reservation.builder().status(ReservationStatus.HOLDING).holdExpiresAt(holdExpiresAt).build();
        Order order = createdOrder(reservation, paymentDeadline);
        GameSeat gameSeat = GameSeat.builder().status(GameSeatStatus.AVAILABLE).build();
        gameSeat.hold(holdExpiresAt);
        List<OrderItem> orderItems = new ArrayList<>();
        OrderItem orderItem = OrderItem.of(order, gameSeat, PRICE);
        orderItems.add(orderItem);

        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        // 조건부 UPDATE가 성공한 경우에만 예약과 좌석의 후속 상태 전이가 진행된다.
        given(
            orderRepository
                .completeCreatedOrder(
                    eq(ORDER_ID),
                    any(LocalDateTime.class),
                    eq(OrderStatus.CREATED),
                    eq(OrderStatus.PAID)
                )
        ).willReturn(completeOrderCount);

        return new CompleteOrderFixture(order, reservation, gameSeat, orderItems);
    }

    // ---------- 결제 완료 ----------

    @Test
    @DisplayName("CREATED 주문을 결제 완료 처리하면 주문 · 예약 · 좌석 상태가 결제완료 상태로 변경된다.")
    void completeOrder_changesOrderReservationAndGameSeatToPaidStatus() {

        // given
        CompleteOrderFixture fixture = givenCompletableOrder(1);
        Order order = fixture.order();
        Reservation reservation = fixture.reservation();
        GameSeat gameSeat = fixture.gameSeat();
        List<OrderItem> orderItems = fixture.orderItems();

        given(orderItemRepository.findByOrder_Id(ORDER_ID)).willReturn(orderItems);

        // when
        orderService.completeOrder(ORDER_ID);

        // then
        // 결제 완료가 확정되면 연결된 예약과 좌석도 같은 처리에서 함께 전이되어야한다.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(gameSeat.getStatus()).isEqualTo(GameSeatStatus.SOLD);
        assertThat(gameSeat.getHoldExpiresAt()).isNull();
        assertThat(gameSeat.getSoldAt()).isNotNull();

        // 만료 처리와 경합할 때 CREATED 상태와 결제 기한을 함께 검사하는 조건부 UPDATE가 호출돼야 한다.
        then(orderRepository)
            .should()
            .completeCreatedOrder(
                eq(ORDER_ID),
                any(LocalDateTime.class),
                eq(OrderStatus.CREATED),
                eq(OrderStatus.PAID)
            );
        then(orderItemRepository).should().findByOrder_Id(ORDER_ID);
    }

    @Test
    @DisplayName("결제 기한이 지난 CREATED 주문을 결제 완료 처리하면 예외가 발생 한다.")
    void completeOrder_throwsExceptionWhenPaymentDeadlineExpired() {

        // given
        LocalDateTime paymentDeadline = LocalDateTime.now().minusMinutes(1);
        Order order = createdOrder(mock(Reservation.class), paymentDeadline);
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.completeOrder(ORDER_ID)).isInstanceOf(OrderExpiredException.class);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

        // 결제 기한이 지난 주문은 조건부 UPDATE와 연관 상태 전이를 시작하지 않는다.
        then(orderRepository)
            .should(never())
            .completeCreatedOrder(
                eq(ORDER_ID),
                any(LocalDateTime.class),
                eq(OrderStatus.CREATED),
                eq(OrderStatus.PAID)
            );
        then(orderItemRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("결제 완료 조건부 UPDATE가 실패하면 예외가 발생한다.")
    void completeOrder_throwsExceptionWhenConditionalUpdateFails() {

        // given
        CompleteOrderFixture fixture = givenCompletableOrder(0);
        Order order = fixture.order();
        Reservation reservation = fixture.reservation();
        GameSeat gameSeat = fixture.gameSeat();

        // when & then
        assertThatThrownBy(() -> orderService.completeOrder(ORDER_ID)).isInstanceOf(InvalidOrderStatusException.class);

        // then
        // 조건부 UPDATE가 실패하면 이미 다른 상태 전이가 확정된 것으로 보고 예약과 좌석을 변경하지 않는다.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HOLDING);
        assertThat(gameSeat.getStatus()).isEqualTo(GameSeatStatus.HELD);
        assertThat(gameSeat.getHoldExpiresAt()).isNotNull();

        then(orderRepository)
            .should()
            .completeCreatedOrder(
                eq(ORDER_ID),
                any(LocalDateTime.class),
                eq(OrderStatus.CREATED),
                eq(OrderStatus.PAID)
            );
        then(orderItemRepository).shouldHaveNoInteractions();
    }

    // ---------- 주문 상태 전이 ----------

    @Test
    @DisplayName("CREATED 주문을 결제 기한 만료 처리하면 EXPIRED 상태로 변경된다.")
    void expireOrder_changesStatusToExpired() {

        Order order = createdOrder(mock(Reservation.class), PAYMENT_DEADLINE);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.expireOrder(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    @DisplayName("CREATED 주문을 종결 결제 실패 처리하면 CANCELED 상태로 변경된다.")
    void failOrder_changesStatusToCanceled() {

        Order order = createdOrder(mock(Reservation.class), PAYMENT_DEADLINE);

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.failOrder(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);

        verify(orderRepository).findById(ORDER_ID);
    }

    @Test
    @DisplayName("존재하지 않는 주문을 결제 완료 처리하면 예외가 발생한다.")
    void completeOrder_throwsExceptionWhenOrderNotFound() {

        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.completeOrder(ORDER_ID)).isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository).findById(ORDER_ID);
    }

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
        assertThat(response.getHoldExpiresAt()).isEqualTo(response.getPaymentDeadline());
    }

    // ---------- 주문 생성 시 선점 만료 시간 연장 ----------

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
        assertThat(response.getHoldExpiresAt()).isEqualTo(holdExpiresAt);
    }

    @Test
    @DisplayName("선점 만료 시간은 최초 선점 후 18분을 넘지 않는다.")
    void createOrder_limitsHoldExpiresAt() {

        // given
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime holdExpiresAt = createdAt.plusMinutes(19);
        LocalDateTime expectedExpiresAt = createdAt.plusMinutes(18);

        givenValidCreateOrder(createdAt, holdExpiresAt);

        // when
        OrderResponse response = orderService.createOrder(USER_ID, RESERVATION_ID);

        // then
        assertThat(response.getHoldExpiresAt()).isEqualTo(expectedExpiresAt);
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
            .allSatisfy(gameSeat -> assertThat(gameSeat.getHoldExpiresAt()).isEqualTo(response.getHoldExpiresAt()));
    }

    @Test
    @DisplayName("결제 기한이 18분 상한을 넘으면 주문 생성에 실패한다.")
    void createOrder_throwsExceptionWhenHoldLimitExceeded() {

        // given
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(11);
        LocalDateTime holdExpiresAt = LocalDateTime.now().plusMinutes(1);

        givenLockedReservation(createdAt, holdExpiresAt);

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(USER_ID, RESERVATION_ID))
            .isInstanceOf(PreReservationExpiredException.class);

        // then
        verify(reservationSeatRepository, never()).findByReservation_Id(RESERVATION_ID);
        verify(orderRepository, never()).save(any(Order.class));
        verify(orderItemRepository, never()).saveAll(any());
        verify(orderReservationRepository, never()).updateHoldExpiresAtById(any(), any(LocalDateTime.class));
    }
}
