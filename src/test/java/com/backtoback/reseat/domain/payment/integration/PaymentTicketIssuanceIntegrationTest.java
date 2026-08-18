package com.backtoback.reseat.domain.payment.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.payment.dto.request.PaymentCompleteRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCompleteResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.entity.PaymentStatus;
import com.backtoback.reseat.domain.payment.entity.PgProvider;
import com.backtoback.reseat.domain.payment.pg.toss.TossPaymentClient;
import com.backtoback.reseat.domain.payment.pg.toss.dto.response.TossPaymentResponse;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.payment.service.PaymentService;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.global.common.BaseIntegrationTest;

import jakarta.persistence.EntityManager;

/**
 * Toss 승인 이후 결제, 주문, 티켓이 실제 서비스 흐름으로 연결되는지 검증한다.
 * <p>외부 시스템인 Toss 승인 응답만 모킹하고 PaymentService, OrderService,
 * TicketService와 각 Repository는 실제 Spring 빈을 사용한다.</p>
 */
@DisplayName("결제 승인 티켓 발급 통합")
class PaymentTicketIssuanceIntegrationTest extends BaseIntegrationTest {

    private static final int AMOUNT = 18_000;
    private static final String IDEMPOTENCY_KEY = "payment-ticket-integration-key";
    private static final String PAYMENT_KEY = "toss-payment-ticket-integration-key";
    private static final String PG_ORDER_ID = "ORD-PAYMENT-TICKET-INTEGRATION";

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    @MockitoBean
    private RedissonClient redissonClient;

    @Test
    @DisplayName("Toss 승인이 완료되면 결제와 주문을 완료하고 주문 항목의 티켓을 발급한다.")
    void completesPaymentOrderAndIssuesTicket() {
        // Given: 좌석 선점과 주문 생성이 끝나고 Toss 승인만 남은 READY 결제를 준비한다.
        PaymentFixture fixture = createFixture();
        PaymentCompleteRequest request = mock(PaymentCompleteRequest.class);
        TossPaymentResponse tossResponse = mock(TossPaymentResponse.class);

        // 프론트가 Toss 인증을 마친 뒤 결제 승인 API에 전달하는 콜백 값을 구성한다.
        when(request.getPaymentKey()).thenReturn(PAYMENT_KEY);
        when(request.getOrderId()).thenReturn(PG_ORDER_ID);
        when(request.getAmount()).thenReturn(AMOUNT);

        // 실제 Toss API 대신 카드 결제가 정상 승인된 응답을 반환하도록 설정한다.
        when(tossResponse.isApproved()).thenReturn(true);
        when(tossResponse.getPaymentKey()).thenReturn(PAYMENT_KEY);
        when(tossResponse.getMethod()).thenReturn("CARD");
        when(tossResponse.getApprovedAt()).thenReturn("2026-08-15T12:00:00+09:00");
        when(tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT)).thenReturn(tossResponse);

        // When: 결제 승인 유스케이스를 실행하면 결제 승인, 주문 완료, 티켓 발급이 차례로 수행된다.
        PaymentCompleteResponse response
            = paymentService.completePayment(fixture.userId(), fixture.paymentId(), IDEMPOTENCY_KEY, request);

        // 서비스가 끝난 뒤 DB를 다시 조회해 실제로 저장된 최종 상태를 확인한다.
        Payment payment = paymentRepository.findById(fixture.paymentId()).orElseThrow();
        Order order = orderRepository.findById(fixture.orderId()).orElseThrow();
        Ticket ticket = ticketRepository.findByOrderItemId(fixture.orderItemId()).orElseThrow();

        // Then: Toss 승인 결과가 로컬 결제와 주문에 반영되고 주문 항목의 티켓까지 발급되어야 한다.
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ISSUED);

        // 클라이언트가 추가 조회 없이 발급 결과를 확인할 수 있도록 승인 응답에도 같은 티켓이 포함되어야 한다.
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.getTickets()).hasSize(1);
        assertThat(response.getTickets().get(0).getTicketId()).isEqualTo(ticket.getId());

        // 로컬 상태만 변경한 것이 아니라 Toss confirm을 정확히 한 번 호출했는지 확인한다.
        verify(tossPaymentClient).confirm(PAYMENT_KEY, PG_ORDER_ID, AMOUNT);
    }

    /**
     * 결제 승인부터 티켓 발급까지 필요한 최소 연관 데이터를 하나의 트랜잭션으로 저장한다.
     */
    private PaymentFixture createFixture() {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();

            // 티켓 응답에 경기와 좌석 정보가 포함되므로 구장, 팀, 경기부터 구성한다.
            Stadium stadium = Stadium.of("결제 티켓 테스트 구장", "테스트시 테스트구", 10_000);
            entityManager.persist(stadium);

            Team homeTeam = Team.of("결제 티켓 홈팀", stadium);
            Team awayTeam = Team.of("결제 티켓 원정팀", stadium);
            entityManager.persist(homeTeam);
            entityManager.persist(awayTeam);

            Game game
                = Game
                    .builder()
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .stadium(stadium)
                    .gameAt(now.plusDays(7))
                    .bookingOpenAt(now.minusDays(1))
                    .bookingCloseAt(now.plusDays(6))
                    .bookingStatus(BookingStatus.OPEN)
                    .title("결제 티켓 발급 테스트 경기")
                    .build();
            entityManager.persist(game);

            // 주문 항목과 발급 티켓이 참조할 실제 경기 좌석을 구성한다.
            SeatZone seatZone = SeatZone.of(stadium, "101", SeatGrade.INFIELD, AMOUNT);
            entityManager.persist(seatZone);
            Seat seat = Seat.of(stadium, seatZone, "101", "A", "1");
            entityManager.persist(seat);

            GameSeat gameSeat = GameSeat.builder().game(game).seat(seat).price(AMOUNT).build();
            gameSeat.hold(now.plusMinutes(10));
            entityManager.persist(gameSeat);

            // 결제와 주문의 소유권 검증에 사용할 사용자를 생성한다.
            User user
                = User
                    .builder()
                    .email("test@example.com")
                    .password("test1234!")
                    .name("결제 티켓 테스트 사용자")
                    .phone("010-1234-5678")
                    .isVerified(true)
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build();
            entityManager.persist(user);

            // 좌석 선점 이후 주문이 생성된 시점을 표현하기 위해 유효한 HOLDING 예약을 준비한다.
            Reservation reservation
                = Reservation
                    .builder()
                    .reservationNo("RSV-PAYMENT-TICKET-INTEGRATION")
                    .user(user)
                    .game(game)
                    .status(ReservationStatus.HOLDING)
                    .holdExpiresAt(now.plusMinutes(10))
                    .build();
            entityManager.persist(reservation);

            // 결제 가능한 CREATED 주문과 해당 좌석을 가리키는 주문 항목을 생성한다.
            Order order = Order.of(PG_ORDER_ID, user, reservation, AMOUNT, now.plusMinutes(8));
            entityManager.persist(order);

            OrderItem orderItem = OrderItem.of(order, gameSeat, AMOUNT);
            entityManager.persist(orderItem);

            // Toss 승인 전 READY 결제를 생성한다. completePayment가 이 결제를 APPROVED로 전이한다.
            Payment payment
                = Payment
                    .builder()
                    .paymentNo("PAY-PAYMENT-TICKET-INTEGRATION")
                    .order(order)
                    .user(user)
                    .amount(AMOUNT)
                    .idempotencyKey(IDEMPOTENCY_KEY)
                    .status(PaymentStatus.READY)
                    .pgProvider(PgProvider.TOSS)
                    .pgOrderId(PG_ORDER_ID)
                    .build();
            entityManager.persist(payment);

            // 테스트 데이터를 본 테스트에서 사용할 수 있도록 INSERT를 DB에 반영한다.
            entityManager.flush();

            return new PaymentFixture(user.getId(), order.getId(), orderItem.getId(), payment.getId());
        });
    }

    /**
     * 테스트에 필요한 식별자만 전달하여 내부 데이터를 직접 사용하지 않고 조회하도록 한다.
     */
    private record PaymentFixture(Long userId, Long orderId, Long orderItemId, Long paymentId) {
    }
}
