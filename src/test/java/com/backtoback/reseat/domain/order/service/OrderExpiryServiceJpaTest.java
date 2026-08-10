package com.backtoback.reseat.domain.order.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
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
import com.backtoback.reseat.global.config.JpaConfig;
import com.backtoback.reseat.global.config.QuerydslConfig;

import jakarta.persistence.EntityManager;

/**
 * 주문 만료 처리의 JPQL 벌크 UPDATE와 연계 상태 전이를 검증한다.
 *
 * <p>벌크 UPDATE 전후 영속성 컨텍스트를 초기화하고
 * DB에서 다시 조회한 상태를 검증한다.</p>
 */
@DataJpaTest
@Import({
    OrderExpiryService.class,
    QuerydslConfig.class,
    JpaConfig.class
})
public class OrderExpiryServiceJpaTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 26, 14, 0);
    private static final LocalDateTime EXPIRED_PAYMENT_DEADLINE = NOW.minusMinutes(1);
    private static final LocalDateTime FUTURE_PAYMENT_DEADLINE = NOW.plusMinutes(1);
    private static final LocalDateTime HOLD_EXPIRES_AT = NOW.plusMinutes(5);
    private static final int PRICE = 18_000;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderExpiryService orderExpiryService;

    private Stadium stadium;
    private User user;
    private Game game;
    private SeatZone seatZone;
    private long fixtureSequence;

    @BeforeEach
    void setUp() {

        stadium = Stadium.of(
            "테스트 구장",
            "테스트시 테스트구",
            10_000);
        entityManager.persist(stadium);

        Team homeTeam = Team.of("홈팀", stadium);
        Team awayTeam = Team.of("원정팀", stadium);
        entityManager.persist(homeTeam);
        entityManager.persist(awayTeam);

        game = Game.builder()
            .homeTeam(homeTeam)
            .awayTeam(awayTeam)
            .stadium(stadium)
            .gameAt(NOW.plusDays(7))
            .bookingOpenAt(NOW.minusDays(1))
            .bookingCloseAt(NOW.plusDays(6))
            .bookingStatus(BookingStatus.OPEN)
            .title("주문 만료 테스트 경기")
            .build();
        entityManager.persist(game);

        seatZone = SeatZone.of(
            stadium,
            "테스트 구역",
            SeatGrade.INFIELD, PRICE);
        entityManager.persist(seatZone);

        user = User.builder()
            .email("order-expiry@test.com")
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
     * 주문 만료 테스트에 필요한 주문 · 예약 · 좌석 연결 데이터를 생성한다.
     *
     * <p>각 Entity를 초기 상태로 생성한 뒤 도메인 메서드를 통해
     * 전달받은 상태로 전이한다.</p>
     *
     * @param paymentDeadline   주문 결제 기한
     * @param orderStatus       주문 상태
     * @param reservationStatus 예약 상태
     * @param gameSeatStatus    경기 좌석 상태 목록
     * @return 생성된 주문, 예약과 경기 좌석 ID
     */
    private OrderExpiryFixture createOrderExpiryFixture(
        LocalDateTime paymentDeadline,
        OrderStatus orderStatus,
        ReservationStatus reservationStatus,
        GameSeatStatus... gameSeatStatus) {

        long sequence = ++fixtureSequence;
        String suffix = String.valueOf(sequence);

        Reservation reservation = Reservation.builder()
            .reservationNo("RSV-EXPIRY-" + suffix)
            .user(user)
            .game(game)
            .status(ReservationStatus.HOLDING)
            .holdExpiresAt(HOLD_EXPIRES_AT)
            .build();

        changeReservationStatus(reservation, reservationStatus);
        entityManager.persist(reservation);

        Order order = Order.of(
            "ORD-EXPIRY-" + suffix,
            user,
            reservation,
            PRICE * gameSeatStatus.length,
            paymentDeadline);

        changeOrderStatus(order, orderStatus);
        entityManager.persist(order);

        List<Long> gameSeatIds = new ArrayList<>();

        for (int i = 0; i < gameSeatStatus.length; i++) {
            Seat seat = Seat.of(
                stadium,
                seatZone,
                "A",
                suffix,
                String.valueOf(i + 1));
            entityManager.persist(seat);

            GameSeat gameSeat = GameSeat.builder()
                .game(game)
                .seat(seat)
                .price(PRICE)
                .status(GameSeatStatus.AVAILABLE)
                .build();

            changeGameSeatStatus(gameSeat, gameSeatStatus[i]);
            entityManager.persist(gameSeat);

            OrderItem orderItem = OrderItem.of(
                order,
                gameSeat,
                PRICE);

            entityManager.persist(orderItem);

            gameSeatIds.add(gameSeat.getId());
        }

        return new OrderExpiryFixture(
            order.getId(),
            reservation.getId(),
            List.copyOf(gameSeatIds));
    }

    @Test
    @DisplayName("결제 기한이 지난 CREATED 주문은 EXPIRED가 되고 예약과 좌석이 해제된다.")
    void expireOrders_expiresOverdueOrderAndReleasesHold() {

        // given
        OrderExpiryFixture overdueCreatedOrderFixture = createOrderExpiryFixture(
            EXPIRED_PAYMENT_DEADLINE,
            OrderStatus.CREATED,
            ReservationStatus.HOLDING,
            GameSeatStatus.HELD, GameSeatStatus.HELD);

        entityManager.flush();
        entityManager.clear();

        // when
        OrderExpiryService.OrderExpiryResult result = orderExpiryService.expireOrders(NOW);

        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(result.expiredOrders())
            .isEqualTo(1);
        assertThat(result.expiredReservations())
            .isEqualTo(1);
        assertThat(result.releasedSeats())
            .isEqualTo(2);
        assertThat(result.total())
            .isEqualTo(4);

        Order expiredOrder = entityManager.find(
            Order.class,
            overdueCreatedOrderFixture.orderId());
        assertThat(expiredOrder.getStatus())
            .isEqualTo(OrderStatus.EXPIRED);

        Reservation expiredReservation = entityManager.find(
            Reservation.class,
            overdueCreatedOrderFixture.reservationId());
        assertThat(expiredReservation.getStatus())
            .isEqualTo(ReservationStatus.EXPIRED);

        List<GameSeat> gameSeats = overdueCreatedOrderFixture.gameSeatIds()
            .stream()
            .map(gameSeatId -> entityManager.find(
                GameSeat.class,
                gameSeatId))
            .toList();

        assertThat(gameSeats)
            .allSatisfy(gameSeat -> {
                assertThat(gameSeat.getStatus())
                    .isEqualTo(GameSeatStatus.AVAILABLE);
                assertThat(gameSeat.getHoldExpiresAt())
                    .isNull();
            });
    }

    @Test
    @DisplayName("결제 기한이 기준 시간과 같으면 만료 처리된다.")
    void expireOrders_expiresOrderAtPaymentDeadline() {

        // given
        OrderExpiryFixture deadlineBoundaryCreatedOrderFixture = createOrderExpiryFixture(
            NOW,
            OrderStatus.CREATED,
            ReservationStatus.HOLDING,
            GameSeatStatus.HELD);

        entityManager.flush();
        entityManager.clear();

        // when
        OrderExpiryService.OrderExpiryResult result = orderExpiryService.expireOrders(NOW);

        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(result.expiredOrders())
            .isEqualTo(1);
        assertThat(result.expiredReservations())
            .isEqualTo(1);
        assertThat(result.releasedSeats())
            .isEqualTo(1);
        assertThat(result.total())
            .isEqualTo(3);

        Order expiredOrder = entityManager.find(
            Order.class,
            deadlineBoundaryCreatedOrderFixture.orderId());
        assertThat(expiredOrder.getStatus())
            .isEqualTo(OrderStatus.EXPIRED);

        Reservation expiredReservation = entityManager.find(
            Reservation.class,
            deadlineBoundaryCreatedOrderFixture.reservationId());
        assertThat(expiredReservation.getStatus())
            .isEqualTo(ReservationStatus.EXPIRED);

        List<GameSeat> gameSeats = deadlineBoundaryCreatedOrderFixture.gameSeatIds()
            .stream()
            .map(gameSeatId -> entityManager.find(
                GameSeat.class,
                gameSeatId))
            .toList();

        assertThat(gameSeats)
            .allSatisfy(gameSeat -> {
                assertThat(gameSeat.getStatus())
                    .isEqualTo(GameSeatStatus.AVAILABLE);
                assertThat(gameSeat.getHoldExpiresAt())
                    .isNull();
            });
    }

    @Test
    @DisplayName("결제 기한 전 주문과 이미 결제된 주문은 만료 처리하지 않는다.")
    void expireOrders_keepsIneligibleOrders() {

        // given
        OrderExpiryFixture futureCreatedOrderFixture = createOrderExpiryFixture(
            FUTURE_PAYMENT_DEADLINE,
            OrderStatus.CREATED,
            ReservationStatus.HOLDING,
            GameSeatStatus.HELD);

        OrderExpiryFixture overdueOrderFixture = createOrderExpiryFixture(
            EXPIRED_PAYMENT_DEADLINE,
            OrderStatus.PAID,
            ReservationStatus.CONFIRMED,
            GameSeatStatus.SOLD);

        entityManager.flush();
        entityManager.clear();

        // when
        OrderExpiryService.OrderExpiryResult result = orderExpiryService.expireOrders(NOW);

        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(result.expiredOrders())
            .isEqualTo(0);
        assertThat(result.expiredReservations())
            .isEqualTo(0);
        assertThat(result.releasedSeats())
            .isEqualTo(0);
        assertThat(result.total())
            .isEqualTo(0);

        Order fetureOrder = entityManager.find(
            Order.class,
            futureCreatedOrderFixture.orderId());
        Order paidOrder = entityManager.find(
            Order.class,
            overdueOrderFixture.orderId());
        assertThat(fetureOrder.getStatus())
            .isEqualTo(OrderStatus.CREATED);
        assertThat(paidOrder.getStatus())
            .isEqualTo(OrderStatus.PAID);

        Reservation holdingReservation = entityManager.find(
            Reservation.class,
            futureCreatedOrderFixture.reservationId());
        Reservation confirmedReservation = entityManager.find(
            Reservation.class,
            overdueOrderFixture.reservationId());
        assertThat(holdingReservation.getStatus())
            .isEqualTo(ReservationStatus.HOLDING);
        assertThat(confirmedReservation.getStatus())
            .isEqualTo(ReservationStatus.CONFIRMED);

        GameSeat heldGameSeat = entityManager.find(
            GameSeat.class,
            futureCreatedOrderFixture.gameSeatIds().get(0));
        GameSeat soldGameSeat = entityManager.find(
            GameSeat.class,
            overdueOrderFixture.gameSeatIds().get(0));
        assertThat(heldGameSeat.getStatus())
            .isEqualTo(GameSeatStatus.HELD);
        assertThat(soldGameSeat.getStatus())
            .isEqualTo(GameSeatStatus.SOLD);
        assertThat(heldGameSeat.getHoldExpiresAt())
            .isEqualTo(HOLD_EXPIRES_AT);
        assertThat(soldGameSeat.getHoldExpiresAt())
            .isNull();
    }

    @Test
    @DisplayName("연결된 예약과 좌석이 대상 상태가 아니면 그대로 유지한다.")
    void expireOrders_keepsReservationAndSeatWithNonTargetStatus() {

        // given
        OrderExpiryFixture overdueOrderWithNonTargetResourcesFixture = createOrderExpiryFixture(
            EXPIRED_PAYMENT_DEADLINE,
            OrderStatus.CREATED,
            ReservationStatus.CANCELED,
            GameSeatStatus.AVAILABLE);

        entityManager.flush();
        entityManager.clear();

        // when
        OrderExpiryService.OrderExpiryResult result = orderExpiryService.expireOrders(NOW);

        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(result.expiredOrders())
            .isEqualTo(1);
        assertThat(result.expiredReservations())
            .isEqualTo(0);
        assertThat(result.releasedSeats())
            .isEqualTo(0);
        assertThat(result.total())
            .isEqualTo(1);

        Order expiredOrder = entityManager.find(
            Order.class,
            overdueOrderWithNonTargetResourcesFixture.orderId());
        assertThat(expiredOrder.getStatus())
            .isEqualTo(OrderStatus.EXPIRED);

        Reservation canceledReservation = entityManager.find(
            Reservation.class,
            overdueOrderWithNonTargetResourcesFixture.reservationId());
        assertThat(canceledReservation.getStatus())
            .isEqualTo(ReservationStatus.CANCELED);

        GameSeat availableGameSeat = entityManager.find(
            GameSeat.class,
            overdueOrderWithNonTargetResourcesFixture.gameSeatIds().get(0));
        assertThat(availableGameSeat.getStatus())
            .isEqualTo(GameSeatStatus.AVAILABLE);
        assertThat(availableGameSeat.getHoldExpiresAt())
            .isNull();
    }

    @Test
    @DisplayName("이미 만료 처리된 주문은 다시 처리하지 않는다.")
    void expireOrders_doesNotProcessExpiredOrderAgain() {

        // given
        OrderExpiryFixture overdueCreatedOrderFixture = createOrderExpiryFixture(
            EXPIRED_PAYMENT_DEADLINE,
            OrderStatus.CREATED,
            ReservationStatus.HOLDING,
            GameSeatStatus.HELD);

        entityManager.flush();
        entityManager.clear();

        OrderExpiryService.OrderExpiryResult firstExpiryResult = orderExpiryService.expireOrders(NOW);

        entityManager.flush();
        entityManager.clear();

        // when
        OrderExpiryService.OrderExpiryResult secondExpiryResult = orderExpiryService.expireOrders(NOW);

        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(firstExpiryResult.expiredOrders())
            .isEqualTo(1);
        assertThat(firstExpiryResult.expiredReservations())
            .isEqualTo(1);
        assertThat(firstExpiryResult.releasedSeats())
            .isEqualTo(1);
        assertThat(firstExpiryResult.total())
            .isEqualTo(3);

        assertThat(secondExpiryResult.expiredOrders())
            .isEqualTo(0);
        assertThat(secondExpiryResult.expiredReservations())
            .isEqualTo(0);
        assertThat(secondExpiryResult.releasedSeats())
            .isEqualTo(0);
        assertThat(secondExpiryResult.total())
            .isEqualTo(0);

        Order expiredOrder = entityManager.find(
            Order.class,
            overdueCreatedOrderFixture.orderId());
        assertThat(expiredOrder.getStatus())
            .isEqualTo(OrderStatus.EXPIRED);

        Reservation expiredReservation = entityManager.find(
            Reservation.class,
            overdueCreatedOrderFixture.reservationId());
        assertThat(expiredReservation.getStatus())
            .isEqualTo(ReservationStatus.EXPIRED);

        GameSeat availableGameSeat = entityManager.find(
            GameSeat.class,
            overdueCreatedOrderFixture.gameSeatIds().get(0));
        assertThat(availableGameSeat.getStatus())
            .isEqualTo(GameSeatStatus.AVAILABLE);
        assertThat(availableGameSeat.getHoldExpiresAt())
            .isNull();
    }

    private void changeReservationStatus(
        Reservation reservation,
        ReservationStatus reservationStatus) {

        switch (reservationStatus) {
            case HOLDING -> {}
            case CONFIRMED -> reservation.confirm();
            case CANCELED -> reservation.cancel();
            case EXPIRED -> reservation.expire();
        }
    }

    private void changeOrderStatus(
        Order order,
        OrderStatus orderStatus) {

        switch (orderStatus) {
            case CREATED -> {}
            case PAID -> order.paid();
            case EXPIRED -> order.expired();
            case CANCELED -> order.cancel();
        }
    }

    private void changeGameSeatStatus(
        GameSeat gameSeat,
        GameSeatStatus gameSeatStatus) {

        switch (gameSeatStatus) {
            case AVAILABLE -> {}
            case HELD -> gameSeat.hold(HOLD_EXPIRES_AT);
            case SOLD -> {
                gameSeat.hold(HOLD_EXPIRES_AT);
                // HELD -> SOLD 상태를 거쳐야 하므로 선점 후 판매 완료 처리를 한다.
                gameSeat.sell();
            }
            case BLOCKED -> gameSeat.updateStatus(GameSeatStatus.BLOCKED);
        }
    }

    private record OrderExpiryFixture(
        Long orderId,
        Long reservationId,
        List<Long> gameSeatIds) {
    }
}
