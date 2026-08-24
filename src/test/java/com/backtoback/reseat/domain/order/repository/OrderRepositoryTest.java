package com.backtoback.reseat.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.global.config.JpaConfig;
import com.backtoback.reseat.global.config.QuerydslConfig;

import jakarta.persistence.EntityManager;

/**
 * OrderRepository의 결제 완료와 만료 조건부 UPDATE를 검증한다.
 * <p>상태와 결제 기한 조건에 따른 DB 변경 결과를 확인한다.</p>
 */
@DataJpaTest
@Import(
    {
        QuerydslConfig.class,
        JpaConfig.class
    }
)
class OrderRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 22, 1, 0);
    private static final LocalDateTime EXPIRED_PAYMENT_DEADLINE = NOW.minusMinutes(1);
    private static final LocalDateTime FUTURE_PAYMENT_DEADLINE = NOW.plusMinutes(1);
    private static final LocalDateTime PAYMENT_DEADLINE_AT_NOW = NOW;
    private static final LocalDateTime HOLD_EXPIRES_AT = NOW.plusMinutes(5);
    private static final int TOTAL_AMOUNT = 34_000;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    private User user;
    private Game game;
    private long fixtureSequence;

    /**
     * 조건부 UPDATE 테스트에 공통으로 사용할 경기와 사용자를 저장하고 fixture 순번을 초기화한다.
     */
    @BeforeEach
    void setUp() {

        Stadium stadium = Stadium.of("테스트 구장", "테스트시 테스트구", 10_000);
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
                .title("주문 Repository 테스트 경기")
                .build();
        entityManager.persist(game);

        user
            = User
                .builder()
                .email("order-repository@test.com")
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
     * 영속성 컨텍스트의 변경 사항을 DB에 반영하고 초기화한다.
     */
    private void flushAndClear() {

        entityManager.flush();
        entityManager.clear();
    }

    /**
     * 조건부 UPDATE 결과를 DB에서 다시 조회할 주문 ID를 전달한다.
     *
     * @param orderId DB에서 다시 조회할 주문 ID
     */
    private record OrderFixture(Long orderId) {
    }

    /**
     * CREATED 상태 주문을 테스트에 필요한 상태로 전이한다.
     *
     * @param order 상태를 변경할 주문
     * @param orderStatus 전이할 주문 상태
     */
    private void changeOrderStatus(Order order, OrderStatus orderStatus) {

        switch (orderStatus) {
            case CREATED -> {}
            case PAID -> order.paid();
            case EXPIRED -> order.expired();
            case CANCELED -> order.cancel();
        }
    }

    /**
     * 결제 완료와 만료 조건부 UPDATE 검증에 필요한 주문과 연관 데이터를 저장한다.
     *
     * @param paymentDeadline 주문 결제 기한
     * @param orderStatus 저장할 주문 상태
     * @return 저장된 주문 ID fixture
     */
    private OrderFixture createOrderFixture(LocalDateTime paymentDeadline, OrderStatus orderStatus) {

        long sequence = ++fixtureSequence;
        String suffix = String.valueOf(sequence);

        // Order는 Reservation을 필수로 가지므로 사용자와 경기에 연결된 HOLDING 예약을 함께 저장한다.
        Reservation reservation
            = Reservation
                .builder()
                .reservationNo("RSV-REPOSITORY-" + suffix)
                .user(user)
                .game(game)
                .status(ReservationStatus.HOLDING)
                .holdExpiresAt(HOLD_EXPIRES_AT)
                .build();

        entityManager.persist(reservation);

        Order order = Order.of("ORD-REPOSITORY-" + suffix, user, reservation, TOTAL_AMOUNT, paymentDeadline);
        changeOrderStatus(order, orderStatus);

        entityManager.persist(order);
        return new OrderFixture(order.getId());
    }

    @Test
    @DisplayName("결제 기한 전 CREATED 주문을 결제 완료 처리하면 PAID 상태로 변경된다.")
    void completeCreatedOrder_updatesEligibleOrderToPaid() {

        // given
        OrderFixture orderFixture = createOrderFixture(FUTURE_PAYMENT_DEADLINE, OrderStatus.CREATED);
        Long orderId = orderFixture.orderId();

        flushAndClear();

        // when
        int updatedOrderCount
            = orderRepository.completeCreatedOrder(orderId, NOW, OrderStatus.CREATED, OrderStatus.PAID);

        flushAndClear();

        // then
        assertThat(updatedOrderCount).isEqualTo(1);

        // 벌크 UPDATE는 영속성 컨텍스트를 우회하므로 DB에서 다시 조회해 변경 결과를 확인한다.
        Order paidOrder = entityManager.find(Order.class, orderId);
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("결제 기한이 지난 CREATED 주문은 결제 완료 처리하지 않는다.")
    void completeCreatedOrder_keepsOrderWhenPaymentDeadlineExpired() {

        // given
        OrderFixture orderFixture = createOrderFixture(EXPIRED_PAYMENT_DEADLINE, OrderStatus.CREATED);
        Long orderId = orderFixture.orderId();

        flushAndClear();

        // when
        int updatedOrderCount
            = orderRepository.completeCreatedOrder(orderId, NOW, OrderStatus.CREATED, OrderStatus.PAID);

        flushAndClear();

        // then
        assertThat(updatedOrderCount).isEqualTo(0);

        // 결제 기한이 지난 주문은 PAID 전이 대상에서 제외돼 만료 처리와의 경합을 막는다.
        Order createdOrder = entityManager.find(Order.class, orderId);
        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("이미 PAID 상태인 주문은 결제 완료 처리하지 않는다.")
    void completeCreatedOrder_keepsOrderWhenOrderStatusIsNotCreated() {

        // given
        OrderFixture orderFixture = createOrderFixture(FUTURE_PAYMENT_DEADLINE, OrderStatus.PAID);
        Long orderId = orderFixture.orderId();

        flushAndClear();

        // when
        int updatedOrderCount
            = orderRepository.completeCreatedOrder(orderId, NOW, OrderStatus.CREATED, OrderStatus.PAID);

        flushAndClear();

        // then
        assertThat(updatedOrderCount).isEqualTo(0);

        // 이미 PAID 상태인 주문은 결제 완료 대상에서 제외돼 중복 상태 전이를 막는다.
        Order paidOrder = entityManager.find(Order.class, orderId);
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("결제 기한이 기준 시간과 같은 CREATED 주문은 결제 완료 처리하지 않는다.")
    void completeCreatedOrder_keepsOrderAtPaymentDeadline() {

        // given
        OrderFixture orderFixture = createOrderFixture(PAYMENT_DEADLINE_AT_NOW, OrderStatus.CREATED);
        Long orderId = orderFixture.orderId();

        flushAndClear();

        // when
        int updatedOrderCount
            = orderRepository.completeCreatedOrder(orderId, NOW, OrderStatus.CREATED, OrderStatus.PAID);

        flushAndClear();

        // then
        assertThat(updatedOrderCount).isEqualTo(0);

        // 결제 기한이 기준 시각과 같으면 만료 대상이므로 PAID 전이에서 제외된다.
        Order createdOrder = entityManager.find(Order.class, orderId);
        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("결제 기한이 지난 CREATED 주문을 만료 처리하면 EXPIRED 상태로 변경된다.")
    void expireCreatedOrder_updatesEligibleOrderToExpired() {

        // given
        OrderFixture orderFixture = createOrderFixture(EXPIRED_PAYMENT_DEADLINE, OrderStatus.CREATED);
        Long orderId = orderFixture.orderId();

        flushAndClear();

        // when
        int updatedOrderCount
            = orderRepository.expireCreatedOrder(orderId, NOW, OrderStatus.CREATED, OrderStatus.EXPIRED);

        flushAndClear();

        // then
        assertThat(updatedOrderCount).isEqualTo(1);

        // 벌크 UPDATE는 영속성 컨텍스트를 우회하므로 DB에서 다시 조회해 변경 결과를 확인한다.
        Order expiredOrder = entityManager.find(Order.class, orderId);
        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("이미 PAID 상태인 주문은 만료 처리하지 않는다.")
    void expireCreatedOrder_keepsOrderWhenOrderStatusIsNotCreated() {

        // given
        OrderFixture orderFixture = createOrderFixture(EXPIRED_PAYMENT_DEADLINE, OrderStatus.PAID);
        Long orderId = orderFixture.orderId();

        flushAndClear();

        // when
        int updatedOrderCount
            = orderRepository.expireCreatedOrder(orderId, NOW, OrderStatus.CREATED, OrderStatus.EXPIRED);

        flushAndClear();

        // then
        assertThat(updatedOrderCount).isEqualTo(0);

        // 이미 PAID 상태인 주문은 만료 대상에서 제외돼 결제 완료 상태를 유지해야 한다.
        Order paidOrder = entityManager.find(Order.class, orderId);
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("만료 처리가 주문을 조회한 뒤 결제가 완료되면 PAID 상태를 EXPIRED로 덮어쓰지 않는다.")
    void expireCreatedOrder_keepsPaidOrderWhenPaymentCompletesAfterExpirationRead() {

        // given
        OrderFixture orderFixture = createOrderFixture(PAYMENT_DEADLINE_AT_NOW, OrderStatus.CREATED);
        Long orderId = orderFixture.orderId();

        // 만료 처리가 CREATED 상태를 먼저 조회한 상황을 재현한다.
        Order readOrder = entityManager.find(Order.class, orderId);
        entityManager.flush();

        // when
        // 조회 이후 결제 완료 조건부 UPDATE가 먼저 PAID 전이를 확정한다.
        int completedOrderCount
            = orderRepository.completeCreatedOrder(orderId, NOW.minusNanos(1), OrderStatus.CREATED, OrderStatus.PAID);
        assertThat(readOrder.getStatus()).isEqualTo(OrderStatus.CREATED);

        // 만료 조건부 UPDATE는 DB의 PAID 상태를 확인해 EXPIRED 전이를 거부해야한다.
        int expiredOrderCount
            = orderRepository.expireCreatedOrder(orderId, NOW, OrderStatus.CREATED, OrderStatus.EXPIRED);

        flushAndClear();

        // then
        assertThat(completedOrderCount).isEqualTo(1);
        assertThat(expiredOrderCount).isEqualTo(0);

        Order paidOrder = entityManager.find(Order.class, orderId);
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
