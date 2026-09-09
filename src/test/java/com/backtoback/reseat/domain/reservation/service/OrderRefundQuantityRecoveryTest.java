package com.backtoback.reseat.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.service.OrderService;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.service.port.TicketCountPort;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketCancelReason;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.service.TicketService;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;

import jakarta.persistence.EntityManager;

/**
 * [이슈 #380] 2매 결제 완료 후 1매를 환불하면 TicketCountPort 집계가 즉시 감소하는지 검증한다.
 * <p>PartialCancelRecoveryHandler.recover()와 동일한 순서(refundOrder → completeTicketRefund)로
 * 호출해 실제 운영 경로를 그대로 재현한다.
 */
@SpringBootTest
@Transactional
class OrderRefundQuantityRecoveryTest {

    private static final int PRICE = 18_000;

    // AdmissionTokenService 등 큐 도메인 빈이 RedissonClient를 요구하므로 컨텍스트 로딩을 위해 Mock으로 대체한다.
    @MockitoBean
    private RedissonClient redissonClient;

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private OrderService orderService;
    @Autowired
    private TicketService ticketService;
    @Autowired
    private TicketCountPort ticketCountPort;

    private User user;
    private Game game;
    private Stadium stadium;
    private SeatZone zone;
    private int seatSequence;
    private int orderSequence;

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
                .gameAt(LocalDateTime.now().plusDays(7))
                .bookingOpenAt(LocalDateTime.now().minusDays(1))
                .bookingCloseAt(LocalDateTime.now().plusDays(6))
                .bookingStatus(BookingStatus.OPEN)
                .title("[이슈 #380] 수량 회복 검증 경기")
                .build();
        entityManager.persist(game);

        zone = SeatZone.of(stadium, "테스트 구역", SeatGrade.INFIELD, PRICE);
        entityManager.persist(zone);

        user
            = User
                .builder()
                .email("quantity-recovery@test.com")
                .password("test")
                .name("수량회복테스트")
                .phone("010-1111-2222")
                .isVerified(true)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);
    }

    @Test
    @DisplayName("should_decreaseActiveTicketCount_when_oneOfTwoIssuedTicketsIsRefunded")
    void should_decreaseActiveTicketCount_when_oneOfTwoIssuedTicketsIsRefunded() {

        // given: 하나의 주문에 2개 항목(=2매 결제 완료)이 있는 실제 다좌석 구매 구조를 재현한다.
        PaidOrderFixture fixture = createPaidOrderWithTickets(2);

        entityManager.flush();
        entityManager.clear();

        assertThat(ticketCountPort.countActiveTickets(user.getId(), game.getId())).isEqualTo(2);

        Long refundedTicketId = fixture.ticketIds().get(0);
        Long refundedOrderItemId = fixture.orderItemIds().get(0);

        // 사용자의 환불 요청 접수 단계
        // 실제 흐름에서는 TicketService.cancelTicket()이 수행하는 ISSUED → REFUND_PENDING 전이다.
        // PG 호출은 이 테스트 범위가 아니므로 도메인 메서드만 직접 호출해 선행 상태를 만든다.
        Ticket ticketToRefund = entityManager.find(Ticket.class, refundedTicketId);
        ticketToRefund.requestRefund(TicketCancelReason.USER_REFUND, null);
        entityManager.flush();
        entityManager.clear();

        // when: PG 취소 성공 후 운영 코드와 동일한 순서로 환불 완료 확정 처리한다.
        orderService.refundOrder(refundedOrderItemId);
        ticketService.completeTicketRefund(refundedTicketId);

        entityManager.flush();
        entityManager.clear();

        // then: 집계가 2 → 1로 감소하고, 대상 티켓만 REFUNDED로 전이된다.
        assertThat(ticketCountPort.countActiveTickets(user.getId(), game.getId())).isEqualTo(1);
        assertThat(entityManager.find(Ticket.class, refundedTicketId).getStatus()).isEqualTo(TicketStatus.REFUNDED);

        // 남은 항목(2번째 티켓)이 아직 ACTIVE이므로 주문은 전체 취소가 아니라 부분 취소여야 한다.
        Order partiallyCanceledOrder = entityManager.find(Order.class, fixture.orderId());
        assertThat(partiallyCanceledOrder.getStatus()).isEqualTo(OrderStatus.PARTIALLY_CANCELED);

        // 아직 환불하지 않은 두 번째 티켓은 ISSUED 상태를 유지해야 한다.
        Long remainingTicketId = fixture.ticketIds().get(1);
        assertThat(entityManager.find(Ticket.class, remainingTicketId).getStatus()).isEqualTo(TicketStatus.ISSUED);
    }

    /**
     * 결제 완료 상태의 주문 1건과, 그 안에 딸린 ticketCount개의 ISSUED 티켓을 생성한다.
     * <p>주문 항목이 여러 개인 경우 {@code OrderService.refundOrder()}가
     * 부분 취소(PARTIALLY_CANCELED)로 분기하는지까지 검증할 수 있도록,
     * 티켓마다 별도 주문을 만들지 않고 하나의 주문에 묶는다.
     */
    private PaidOrderFixture createPaidOrderWithTickets(int ticketCount) {
        orderSequence++;
        String orderSuffix = String.valueOf(orderSequence);

        Reservation reservation
            = Reservation
                .builder()
                .reservationNo("RSV-QR-" + orderSuffix)
                .user(user)
                .game(game)
                .status(ReservationStatus.HOLDING)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        reservation.confirm();
        entityManager.persist(reservation);

        Order order
            = Order
                .of(
                    "ORD-QR-" + orderSuffix,
                    user,
                    reservation,
                    PRICE * ticketCount,
                    LocalDateTime.now().plusMinutes(8)
                );
        order.paid();
        entityManager.persist(order);

        List<Long> ticketIds = new ArrayList<>();
        List<Long> orderItemIds = new ArrayList<>();

        for (int i = 0; i < ticketCount; i++) {
            seatSequence++;
            String seatSuffix = String.valueOf(seatSequence);

            Seat seat = Seat.of(stadium, zone, "A", "1", seatSuffix);
            entityManager.persist(seat);

            GameSeat gameSeat
                = GameSeat.builder().game(game).seat(seat).price(PRICE).status(GameSeatStatus.AVAILABLE).build();
            gameSeat.hold(LocalDateTime.now().plusMinutes(10));
            gameSeat.sell();
            entityManager.persist(gameSeat);

            OrderItem orderItem = OrderItem.of(order, gameSeat, PRICE);
            entityManager.persist(orderItem);

            Ticket ticket = Ticket.issue("TKT-QR-" + seatSuffix, user, orderItem, gameSeat, "QR-QR-" + seatSuffix);
            entityManager.persist(ticket);

            ticketIds.add(ticket.getId());
            orderItemIds.add(orderItem.getId());
        }

        return new PaidOrderFixture(order.getId(), ticketIds, orderItemIds);
    }

    /**
     * 테스트 준비 데이터를 재조회하는 데 필요한 식별자를 보관한다.
     */
    private record PaidOrderFixture(Long orderId, List<Long> ticketIds, List<Long> orderItemIds) {
    }
}
