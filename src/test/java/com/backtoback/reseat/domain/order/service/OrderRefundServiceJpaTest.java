package com.backtoback.reseat.domain.order.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.entity.OrderItemStatus;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.global.common.BaseIntegrationTest;

import jakarta.persistence.EntityManager;

/**
 * 티켓 단위 환불에 따른 주문, 주문 항목, 예약과 좌석의 저장 상태를 검증한다.
 * <p>환불 처리 후 영속성 컨텍스트를 초기화하고
 * DB에서 다시 조회한 상태를 검증한다.</p>
 */
@Transactional
class OrderRefundServiceJpaTest extends BaseIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 21, 0);
    private static final LocalDateTime PAYMENT_DEADLINE = NOW.plusMinutes(8);
    private static final LocalDateTime HOLD_EXPIRES_AT = NOW.plusMinutes(10);
    private static final int PRICE = 18_000;

    // test 프로필에는 RedissonClient Bean이 없으므로 환불 테스트와 무관한 분산락 의존성만 Mock으로 대체한다.
    @MockitoBean
    private RedissonClient redissonClient;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderService orderService;

    private Stadium stadium;
    private User user;
    private Game game;
    private SeatZone seatZone;
    private long fixtureSequence;

    @BeforeEach
    void setUp() {

        stadium = Stadium.of("테스트 구장", "테스트시 테스트구", 10_000);
        entityManager.persist(stadium);

        Team homeTeam = Team.of("홈팀", stadium);
        Team awayTeam = Team.of("원정팀", stadium);
        entityManager.persist(homeTeam);
        entityManager.persist(awayTeam);

        game
            = Game
                .builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium(stadium)
                .gameAt(NOW.plusDays(7))
                .bookingOpenAt(NOW.minusDays(1))
                .bookingCloseAt(NOW.plusDays(6))
                .bookingStatus(BookingStatus.OPEN)
                .title("주문 환불 테스트 경기")
                .build();
        entityManager.persist(game);

        seatZone = SeatZone.of(stadium, "테스트 구역", SeatGrade.INFIELD, PRICE);
        entityManager.persist(seatZone);

        user
            = User
                .builder()
                .email("order-refund@test.com")
                .password("test")
                .name("테스트 사용자")
                .phone("010-1234-5678")
                .isVerified(true)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);

        fixtureSequence = 0L;
    }

    /**
     * 주문 환불 후 저장 상태를 다시 조회하는 데 필요한 식별자를 보관한다.
     *
     * @param orderId 주문 ID
     * @param reservationId 예약 ID
     * @param orderItemIds 주문 항목 ID 목록
     * @param gameSeatIds 경기 좌석 ID 목록
     */
    private record OrderRefundFixture(
        Long orderId,
        Long reservationId,
        List<Long> orderItemIds,
        List<Long> gameSeatIds
    ) {
    }

    /**
     * 주문 환불 테스트에 필요한 결제 완료 주문과 연관 데이터를 생성한다.
     * <p>CONFIRMED 예약, PAID 주문, ACTIVE 주문 항목과 SOLD 경기 좌석을
     * 주문 항목 수에 맞게 생성한다.</p>
     *
     * @param orderItemCount 생성할 주문 항목 수
     * @return 생성된 주문, 예약, 주문 항목과 경기 좌석 ID
     */
    private OrderRefundFixture createOrderRefundFixture(int orderItemCount) {

        long sequence = ++fixtureSequence;
        String suffix = String.valueOf(sequence);

        Reservation reservation
            = Reservation
                .builder()
                .reservationNo("RSV-REFUND-" + suffix)
                .user(user)
                .game(game)
                .status(ReservationStatus.HOLDING)
                .holdExpiresAt(HOLD_EXPIRES_AT)
                .build();
        reservation.confirm();
        entityManager.persist(reservation);

        Order order = Order.of("ORD-REFUND-" + suffix, user, reservation, PRICE * orderItemCount, PAYMENT_DEADLINE);
        order.paid();
        entityManager.persist(order);

        List<Long> gameSeatIds = new ArrayList<>();
        List<Long> orderItemIds = new ArrayList<>();

        for (int i = 0; i < orderItemCount; i++) {
            Seat seat = Seat.of(stadium, seatZone, "A", suffix, String.valueOf(i + 1));
            entityManager.persist(seat);

            // HELD → SOLD 상태를 거쳐야 하므로 선점 후 판매 완료 처리를 한다.
            GameSeat gameSeat
                = GameSeat.builder().game(game).seat(seat).price(PRICE).status(GameSeatStatus.AVAILABLE).build();
            gameSeat.hold(HOLD_EXPIRES_AT);
            gameSeat.sell();
            entityManager.persist(gameSeat);

            OrderItem orderItem = OrderItem.of(order, gameSeat, PRICE);
            entityManager.persist(orderItem);

            gameSeatIds.add(gameSeat.getId());
            orderItemIds.add(orderItem.getId());
        }

        return new OrderRefundFixture(order.getId(), reservation.getId(), orderItemIds, gameSeatIds);
    }

    @Test
    @DisplayName("2개 주문 항목 중 첫 번째 항목을 환불하면 주문과 대상 항목만 부분 취소된다.")
    void refundOrder_partiallyCancelsTwoItemOrder() {

        // given
        OrderRefundFixture twoItemPaidOrderFixture = createOrderRefundFixture(2);

        Long orderId = twoItemPaidOrderFixture.orderId();
        Long reservationId = twoItemPaidOrderFixture.reservationId();
        Long firstOrderItemId = twoItemPaidOrderFixture.orderItemIds().get(0);
        Long secondOrderItemId = twoItemPaidOrderFixture.orderItemIds().get(1);
        Long firstGameSeatId = twoItemPaidOrderFixture.gameSeatIds().get(0);
        Long secondGameSeatId = twoItemPaidOrderFixture.gameSeatIds().get(1);

        // 서비스가 저장된 데이터를 다시 조회하도록 fixture를 DB에 반영하고 영속성 컨텍스트를 초기화한다.
        entityManager.flush();
        entityManager.clear();

        // when
        orderService.refundOrder(firstOrderItemId);

        // 변경 감지 결과를 DB에 반영한 뒤 영속성 컨텍스트를 초기화해 저장 상태를 다시 조회한다.
        entityManager.flush();
        entityManager.clear();

        // then
        Order partiallyCanceledOrder = entityManager.find(Order.class, orderId);
        OrderItem canceledOrderItem = entityManager.find(OrderItem.class, firstOrderItemId);
        OrderItem activeOrderItem = entityManager.find(OrderItem.class, secondOrderItemId);
        GameSeat availableGameSeat = entityManager.find(GameSeat.class, firstGameSeatId);
        GameSeat soldGameSeat = entityManager.find(GameSeat.class, secondGameSeatId);
        Reservation confirmedReservation = entityManager.find(Reservation.class, reservationId);

        assertThat(partiallyCanceledOrder.getStatus()).isEqualTo(OrderStatus.PARTIALLY_CANCELED);
        assertThat(canceledOrderItem.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
        assertThat(activeOrderItem.getStatus()).isEqualTo(OrderItemStatus.ACTIVE);
        assertThat(availableGameSeat.getStatus()).isEqualTo(GameSeatStatus.AVAILABLE);
        assertThat(soldGameSeat.getStatus()).isEqualTo(GameSeatStatus.SOLD);
        assertThat(confirmedReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("부분 취소 주문의 마지막 항목을 환불하면 주문과 예약이 최종 취소된다.")
    void refundOrder_cancelsOrderAfterLastItem() {

        // given
        OrderRefundFixture twoItemPaidOrderFixture = createOrderRefundFixture(2);

        Long orderId = twoItemPaidOrderFixture.orderId();
        Long reservationId = twoItemPaidOrderFixture.reservationId();
        Long firstOrderItemId = twoItemPaidOrderFixture.orderItemIds().get(0);
        Long secondOrderItemId = twoItemPaidOrderFixture.orderItemIds().get(1);
        Long firstGameSeatId = twoItemPaidOrderFixture.gameSeatIds().get(0);
        Long secondGameSeatId = twoItemPaidOrderFixture.gameSeatIds().get(1);

        // 서비스가 저장된 데이터를 다시 조회하도록 fixture를 DB에 반영하고 영속성 컨텍스트를 초기화한다.
        entityManager.flush();
        entityManager.clear();

        // 첫 번째 환불을 완료해 마지막 주문 항목 환불 조건을 준비한다.
        orderService.refundOrder(firstOrderItemId);

        // 첫 번째 환불 결과를 DB에 반영하고 두 번째 환불이 변경된 상태를 다시 조회하도록 초기화한다.
        entityManager.flush();
        entityManager.clear();

        // when
        orderService.refundOrder(secondOrderItemId);

        // 변경 감지 결과를 DB에 반영한 뒤 영속성 컨텍스트를 초기화해 저장 상태를 다시 조회한다.
        entityManager.flush();
        entityManager.clear();

        // then
        Order canceledOrder = entityManager.find(Order.class, orderId);
        OrderItem firstCanceledOrderItem = entityManager.find(OrderItem.class, firstOrderItemId);
        OrderItem secondCanceledOrderItem = entityManager.find(OrderItem.class, secondOrderItemId);
        GameSeat firstAvailableGameSeat = entityManager.find(GameSeat.class, firstGameSeatId);
        GameSeat secondAvailableGameSeat = entityManager.find(GameSeat.class, secondGameSeatId);
        Reservation canceledReservation = entityManager.find(Reservation.class, reservationId);

        assertThat(canceledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(firstCanceledOrderItem.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
        assertThat(secondCanceledOrderItem.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
        assertThat(firstAvailableGameSeat.getStatus()).isEqualTo(GameSeatStatus.AVAILABLE);
        assertThat(secondAvailableGameSeat.getStatus()).isEqualTo(GameSeatStatus.AVAILABLE);
        assertThat(canceledReservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
    }

    @Test
    @DisplayName("주문 항목이 1개인 주문을 환불하면 주문과 예약이 최종 취소된다.")
    void refundOrder_cancelsSingleItemOrder() {

        // given
        OrderRefundFixture singleItemPaidOrderFixture = createOrderRefundFixture(1);

        Long orderId = singleItemPaidOrderFixture.orderId();
        Long reservationId = singleItemPaidOrderFixture.reservationId();
        Long orderItemId = singleItemPaidOrderFixture.orderItemIds().get(0);
        Long gameSeatId = singleItemPaidOrderFixture.gameSeatIds().get(0);

        // 서비스가 저장된 데이터를 다시 조회하도록 fixture를 DB에 반영하고 영속성 컨텍스트를 초기화한다.
        entityManager.flush();
        entityManager.clear();

        // when
        orderService.refundOrder(orderItemId);

        // 변경 감지 결과를 DB에 반영한 뒤 영속성 컨텍스트를 초기화해 저장 상태를 다시 조회한다.
        entityManager.flush();
        entityManager.clear();

        // then
        Order canceledOrder = entityManager.find(Order.class, orderId);
        OrderItem canceledOrderItem = entityManager.find(OrderItem.class, orderItemId);
        GameSeat availableGameSeat = entityManager.find(GameSeat.class, gameSeatId);
        Reservation canceledReservation = entityManager.find(Reservation.class, reservationId);

        assertThat(canceledOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(canceledOrderItem.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
        assertThat(availableGameSeat.getStatus()).isEqualTo(GameSeatStatus.AVAILABLE);
        assertThat(canceledReservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
    }
}
