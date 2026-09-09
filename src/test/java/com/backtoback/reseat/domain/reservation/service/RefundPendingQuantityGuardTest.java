package com.backtoback.reseat.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.repository.OrderItemRepository;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.queue.service.AdmissionTokenService;
import com.backtoback.reseat.domain.queue.service.AdmissionTokenTiming;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.exception.MaxSeatCountExceededException;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.stadium.repository.SeatRepository;
import com.backtoback.reseat.domain.stadium.repository.SeatZoneRepository;
import com.backtoback.reseat.domain.stadium.repository.StadiumRepository;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.team.repository.TeamRepository;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketCancelReason;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.common.BaseIntegrationTest;

/**
 * [이슈 #380]
 * REFUND_PENDING·REFUND_FAILED 구간에서는 수량이 회복되지 않아
 * 재선점 시도가 MaxSeatCountExceededException으로 차단됨을 고정하는 회귀 테스트.
 */
class RefundPendingQuantityGuardTest extends BaseIntegrationTest {

    private static final int PRICE = 18_000;
    private static final String TOKEN = "qt_refund-guard-test";

    @MockitoBean
    private AdmissionTokenService admissionTokenService;

    @Autowired
    private SeatHoldFacade seatHoldFacade;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private GameSeatRepository gameSeatRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private SeatZoneRepository seatZoneRepository;
    @Autowired
    private StadiumRepository stadiumRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private TicketRepository ticketRepository;

    private Long userId;
    private Long gameId;
    private Stadium stadium;
    private Game game;
    private SeatZone zone;
    private Long thirdGameSeatId; // 차단 대상 재선점 시도용 3번째 좌석

    static Stream<Arguments> blockedTicketStatuses() {
        return Stream.of(Arguments.of(TicketStatus.REFUND_PENDING), Arguments.of(TicketStatus.REFUND_FAILED));
    }

    @BeforeEach
    void setUp() {
        doNothing().when(admissionTokenService).validateToken(anyLong(), anyLong(), anyString());
        when(admissionTokenService.getTokenTiming(anyLong(), anyLong(), anyString()))
            .thenReturn(new AdmissionTokenTiming(LocalDateTime.now().plusMinutes(21), null));

        stadium = Stadium.of("테스트 구장", "테스트시 테스트구", 10_000);
        stadiumRepository.save(stadium);

        Team homeTeam = Team.of("홈팀", stadium);
        Team awayTeam = Team.of("원정팀", stadium);
        teamRepository.save(homeTeam);
        teamRepository.save(awayTeam);

        game
            = Game
                .builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium(stadium)
                .gameAt(LocalDateTime.now().plusDays(7))
                .bookingOpenAt(LocalDateTime.now().minusHours(1))
                .bookingCloseAt(LocalDateTime.now().plusDays(6))
                .bookingStatus(BookingStatus.OPEN)
                .title("[이슈 #380] 환불 미완료 구간 차단 검증 경기")
                .build();
        gameRepository.save(game);
        gameId = game.getId();

        zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, PRICE);
        seatZoneRepository.save(zone);

        Seat thirdSeat = Seat.of(stadium, zone, "A", "1", "3");
        seatRepository.save(thirdSeat);
        GameSeat thirdGameSeat
            = GameSeat.builder().game(game).seat(thirdSeat).price(PRICE).status(GameSeatStatus.AVAILABLE).build();
        gameSeatRepository.save(thirdGameSeat);
        thirdGameSeatId = thirdGameSeat.getId();

        User user
            = User
                .builder()
                .email("refund-guard@test.com")
                .password("pw")
                .name("환불차단테스트")
                .phone("010-5555-6666")
                .isVerified(true)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);
        userId = user.getId();
    }

    @ParameterizedTest(name = "should_blockHoldAgain_when_ticketStatusIs_{0}")
    @MethodSource("blockedTicketStatuses")
    void should_blockHoldAgain_when_refundIsNotCompleted(TicketStatus targetStatus) {

        // given: ISSUED 1매 + (REFUND_PENDING 또는 REFUND_FAILED) 1매 — 누적 상한과 동일한 "실질 보유 2매"
        createTicket(TicketStatus.ISSUED, "1");
        createTicket(targetStatus, "2");

        // when / then: 3매째(다른 좌석) 선점 시도는 차단되어야 한다.
        assertThatThrownBy(
            () -> seatHoldFacade.holdSeats(userId, TOKEN, new SeatHoldRequest(gameId, List.of(thirdGameSeatId)))
        ).isInstanceOf(MaxSeatCountExceededException.class);

        assertThat(gameSeatRepository.findById(thirdGameSeatId).orElseThrow().getStatus())
            .isEqualTo(GameSeatStatus.AVAILABLE);
    }

    /**
     * 지정한 상태의 티켓 1매와 그에 딸린 결제 완료 주문·좌석을 생성한다.
     * <p>BaseIntegrationTest는 트랜잭션을 미리 열어두지 않으므로 EntityManager를 직접 쓰지 않고,
     * 각 엔티티를 자바 객체 상태에서 원하는 최종 상태까지 전이시킨 뒤 Repository.save()로 한 번에 커밋한다.
     */
    private Ticket createTicket(TicketStatus targetStatus, String seatNumber) {
        User user = userRepository.findById(userId).orElseThrow();

        Seat seat = Seat.of(stadium, zone, "A", "1", seatNumber);
        seatRepository.save(seat);

        GameSeat gameSeat
            = GameSeat.builder().game(game).seat(seat).price(PRICE).status(GameSeatStatus.AVAILABLE).build();
        gameSeat.hold(LocalDateTime.now().plusMinutes(10));
        gameSeat.sell();
        gameSeatRepository.save(gameSeat);

        Reservation reservation
            = Reservation
                .builder()
                .reservationNo("RSV-GUARD-" + seatNumber)
                .user(user)
                .game(game)
                .status(ReservationStatus.HOLDING)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        reservation.confirm();
        reservationRepository.save(reservation);

        Order order = Order.of("ORD-GUARD-" + seatNumber, user, reservation, PRICE, LocalDateTime.now().plusMinutes(8));
        order.paid();
        orderRepository.save(order);

        OrderItem orderItem = OrderItem.of(order, gameSeat, PRICE);
        orderItemRepository.save(orderItem);

        Ticket ticket = Ticket.issue("TKT-GUARD-" + seatNumber, user, orderItem, gameSeat, "QR-GUARD-" + seatNumber);
        applyStatus(ticket, targetStatus);
        ticketRepository.save(ticket);
        return ticket;
    }

    private void applyStatus(Ticket ticket, TicketStatus targetStatus) {
        switch (targetStatus) {
            case ISSUED -> {}
            case REFUND_PENDING -> ticket.requestRefund(TicketCancelReason.USER_REFUND, null);
            case REFUND_FAILED -> {
                ticket.requestRefund(TicketCancelReason.USER_REFUND, null);
                ticket.failRefund();
            }
            default -> throw new IllegalArgumentException("이 테스트에서 지원하지 않는 상태: " + targetStatus);
        }
    }
}
