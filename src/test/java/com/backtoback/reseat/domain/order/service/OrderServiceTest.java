package com.backtoback.reseat.domain.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

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
import com.backtoback.reseat.domain.order.entity.OrderItemStatus;
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
import com.backtoback.reseat.domain.reservation.service.ReservationService;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.service.GameSeatStatusService;
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
    private static final Long ORDER_ITEM_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long RESERVATION_ID = 1L;
    private static final Long GAME_SEAT_ID = 1L;

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

    @Mock
    private ReservationService reservationService;

    @Mock
    private GameSeatStatusService gameSeatStatusService;

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

    /**
     * 주문 만료 조건부 UPDATE 결과에 따른 주문과 Repository 응답을 준비한다.
     *
     * @param expiredOrderCount 만료 조건부 UPDATE가 반환할 변경 주문 수
     * @return 만료 처리 후 상태를 검증할 주문
     */
    private Order givenExpirableOrder(int expiredOrderCount) {

        Order order = createdOrder(mock(Reservation.class), PAYMENT_DEADLINE);

        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(
            orderRepository
                .expireCreatedOrder(
                    eq(ORDER_ID),
                    any(LocalDateTime.class),
                    eq(OrderStatus.CREATED),
                    eq(OrderStatus.EXPIRED)
                )
        ).willReturn(expiredOrderCount);

        return order;
    }

    /**
     * 환불 상태 전이를 검증할 주문, 주문 항목, 좌석과 예약을 함께 전달한다.
     *
     * @param order 환불 후 상태를 검증할 주문
     * @param orderItem 환불 대상 주문 항목
     * @param gameSeat 환불 대상 주문 항목과 연결된 경기 좌석
     * @param reservation 최종 환불 후 상태를 검증할 예약
     */
    private record RefundOrderFixture(Order order, OrderItem orderItem, GameSeat gameSeat, Reservation reservation) {
    }

    /**
     * 남은 주문 항목 존재 여부에 따른 주문, 주문 항목, 좌석과 Repository 응답을 준비한다.
     *
     * @param hasRemainingOrderItems 환불 후 ACTIVE 주문 항목이 남아 있는지 여부
     * @return 환불 후 상태를 검증할 주문, 주문 항목, 좌석과 예약 fixture
     */
    private RefundOrderFixture givenRefundableOrder(boolean hasRemainingOrderItems) {

        Reservation reservation = Reservation.builder().status(ReservationStatus.CONFIRMED).build();
        ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);

        // 남은 주문 항목 조회에 사용하는 주문 ID를 테스트 조건에 맞게 설정한다.
        Order order = createdOrder(reservation, PAYMENT_DEADLINE);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.paid();

        GameSeat gameSeat = GameSeat.builder().status(GameSeatStatus.SOLD).build();
        ReflectionTestUtils.setField(gameSeat, "id", GAME_SEAT_ID);
        OrderItem orderItem = OrderItem.of(order, gameSeat, PRICE);

        given(orderRepository.findByOrderItemIdWithPessimisticWriteLock(eq(ORDER_ITEM_ID)))
            .willReturn(Optional.of(order));

        given(orderItemRepository.findById(ORDER_ITEM_ID)).willReturn(Optional.of(orderItem));

        // 환불 후 남은 ACTIVE 주문 항목의 존재 여부로 주문 상태 전이 경로를 구분한다.
        given(orderItemRepository.existsByOrder_IdAndStatus(ORDER_ID, OrderItemStatus.ACTIVE))
            .willReturn(hasRemainingOrderItems);

        return new RefundOrderFixture(order, orderItem, gameSeat, reservation);
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
    @DisplayName("결제 기한이 지난 CREATED 주문을 만료 처리하면 EXPIRED 상태로 변경된다.")
    void expireOrder_changesStatusToExpired() {

        // given
        // 조건부 UPDATE가 성공한 경우에만 영속성 컨텍스트의 주문 상태를 동기화 한다.
        Order order = givenExpirableOrder(1);

        // when
        orderService.expireOrder(ORDER_ID);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        then(orderRepository).should().findById(ORDER_ID);

        // 만료 처리도 CREATED 상태와 결제 기한을 함께 확인하는 조건부 UPDATE를 호출해야 한다.
        then(orderRepository)
            .should()
            .expireCreatedOrder(
                eq(ORDER_ID),
                any(LocalDateTime.class),
                eq(OrderStatus.CREATED),
                eq(OrderStatus.EXPIRED)
            );
    }

    @Test
    @DisplayName("주문 만료 조건부 UPDATE가 실패하면 예외가 발생하고 주문 상태를 변경하지 않는다.")
    void expireOrder_throwsExceptionWhenConditionalUpdateFails() {

        // given
        Order order = givenExpirableOrder(0);

        // when & then
        assertThatThrownBy(() -> orderService.expireOrder(ORDER_ID)).isInstanceOf(InvalidOrderStatusException.class);

        // then
        // 결제 완료 처리와 경합해 조건부 UPDATE가 실패하면 주문 상태를 덮어쓰지 않는다.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);

        then(orderRepository)
            .should()
            .expireCreatedOrder(
                eq(ORDER_ID),
                any(LocalDateTime.class),
                eq(OrderStatus.CREATED),
                eq(OrderStatus.EXPIRED)
            );
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

    // ---------- 주문 환불 ----------

    @Test
    @DisplayName("일부 티켓 환불이 완료되고 남은 주문 항목이 있으면 주문이 PARTIALLY_CANCELED 상태로 변경된다.")
    void refundOrder_partiallyCancelsOrder() {

        // given
        // 환불 대상 외에 ACTIVE 주문 항목이 남아 있는 부분 취소 조건을 준비한다.
        RefundOrderFixture fixture = givenRefundableOrder(true);
        Order order = fixture.order();
        OrderItem orderItem = fixture.orderItem();

        // when
        orderService.refundOrder(ORDER_ITEM_ID);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_CANCELED);
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.CANCELED);

        then(gameSeatStatusService).should().refundSeat(GAME_SEAT_ID);
        then(reservationService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("마지막 티켓 환불이 완료되면 주문이 CANCELED 상태로 변경된다.")
    void refundOrder_cancelsOrderAfterLastItem() {

        // given
        // 환불 대상 취소 후 ACTIVE 주문 항목이 남지 않는 최종 취소 조건을 준비한다.
        RefundOrderFixture fixture = givenRefundableOrder(false);
        Order order = fixture.order();
        OrderItem orderItem = fixture.orderItem();

        // when
        orderService.refundOrder(ORDER_ITEM_ID);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.CANCELED);

        then(gameSeatStatusService).should().refundSeat(GAME_SEAT_ID);
        then(reservationService).should().cancelConfirmed(RESERVATION_ID);
    }

    /**
     * 결제 완료 후 전액 취소 상태 전이를 검증할 주문, 좌석과 예약을 함께 전달한다.
     *
     * @param order 취소 후 CANCELED 상태를 검증할 주문
     * @param gameSeat 취소 대상 경기 좌석
     * @param reservation 취소 후 상태를 검증할 예약
     */
    private record CancelPaidOrderFixture(Order order, GameSeat gameSeat, Reservation reservation) {
    }

    private CancelPaidOrderFixture givenPaidOrder() {

        Reservation reservation = Reservation.builder().status(ReservationStatus.CONFIRMED).build();
        ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);

        Order order = createdOrder(reservation, PAYMENT_DEADLINE);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.paid();

        GameSeat gameSeat = GameSeat.builder().status(GameSeatStatus.SOLD).build();
        ReflectionTestUtils.setField(gameSeat, "id", GAME_SEAT_ID);
        OrderItem orderItem = OrderItem.of(order, gameSeat, PRICE);

        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));
        given(orderItemRepository.findByOrder_Id(ORDER_ID)).willReturn(List.of(orderItem));

        return new CancelPaidOrderFixture(order, gameSeat, reservation);
    }

    @Test
    @DisplayName("결제 완료된 주문을 전액 취소하면 주문이 CANCELED로 변경되고 좌석이 환불된다.")
    void cancelPaidOrder_cancelsOrderAndRefundsSeat() {

        // given
        CancelPaidOrderFixture fixture = givenPaidOrder();
        Order order = fixture.order();

        // when
        orderService.cancelPaidOrder(ORDER_ID);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);

        then(reservationService).should().cancelConfirmed(RESERVATION_ID);
        then(gameSeatStatusService).should().refundSeat(GAME_SEAT_ID);
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
