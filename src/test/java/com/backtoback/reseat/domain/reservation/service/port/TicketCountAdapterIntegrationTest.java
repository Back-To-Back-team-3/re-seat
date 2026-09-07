package com.backtoback.reseat.domain.reservation.service.port;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

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
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.ticket.entity.Ticket;
import com.backtoback.reseat.domain.ticket.entity.TicketCancelReason;
import com.backtoback.reseat.domain.ticket.entity.TicketStatus;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;

import jakarta.persistence.EntityManager;

/**
 * TicketCountAdapter의 실제 빈 조립(Adapter → TicketService → TicketRepository)을 검증한다.
 * <p>Mock 없이 실제 객체를 그대로 사용하므로 SeatHoldFacadeGateTest(Mock 기반)가 놓치는 빈 등록 누락까지 잡아낸다.
 * Queue-Token·분산락 경로는 검증 범위에서 제외한다.
 * (SeatHoldFacadeGateTest·SeatHoldFacadeConcurrencyTest가 이미 커버)
 */
@SpringBootTest
@Transactional
class TicketCountAdapterIntegrationTest {

    @MockitoBean
    private RedissonClient redissonClient;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TicketCountAdapter ticketCountAdapter;

    private Stadium stadium;
    private Team homeTeam;
    private Team awayTeam;
    private SeatZone zone;
    private long fixtureSequence;

    @BeforeEach
    void setUp() {
        stadium = Stadium.of("테스트 구장", "테스트시 테스트구", 10_000);
        entityManager.persist(stadium);

        homeTeam = Team.of("홈팀", stadium);
        awayTeam = Team.of("원정팀", stadium);
        entityManager.persist(homeTeam);
        entityManager.persist(awayTeam);

        zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, 18_000);
        entityManager.persist(zone);
    }

    @Test
    @DisplayName("should_returnRealCount_when_userHasIssuedAndRefundPendingTicket")
    void should_returnRealCount_when_userHasIssuedAndRefundPendingTicket() {
        // given: ISSUED 1매 + REFUND_PENDING 1매 보유
        User user = createUser("user1@test.com");
        Game game = createGame();
        createTicket(user, game, TicketStatus.ISSUED);
        createTicket(user, game, TicketStatus.REFUND_PENDING);

        // when
        int count = ticketCountAdapter.countActiveTickets(user.getId(), game.getId());

        // then
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("should_returnZero_when_allTicketsAreRefunded")
    void should_returnZero_when_allTicketsAreRefunded() {
        // given: REFUNDED 2매 보유(수량 회복 완료)
        User user = createUser("user2@test.com");
        Game game = createGame();
        createTicket(user, game, TicketStatus.REFUNDED);
        createTicket(user, game, TicketStatus.REFUNDED);

        // when
        int count = ticketCountAdapter.countActiveTickets(user.getId(), game.getId());

        // then
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("should_notCountOtherGameOrOtherUserTickets")
    void should_notCountOtherGameOrOtherUserTickets() {
        // given: 대상 사용자·경기에는 ISSUED 1건, 다른 사용자·다른 경기에는 각각 ISSUED 1건씩
        User targetUser = createUser("target@test.com");
        User otherUser = createUser("other@test.com");
        Game targetGame = createGame();
        Game otherGame = createGame();

        createTicket(targetUser, targetGame, TicketStatus.ISSUED);
        createTicket(otherUser, targetGame, TicketStatus.ISSUED);
        createTicket(targetUser, otherGame, TicketStatus.ISSUED);

        // when
        int count = ticketCountAdapter.countActiveTickets(targetUser.getId(), targetGame.getId());

        // then
        assertThat(count).isEqualTo(1);
    }

    private User createUser(String email) {
        User user
            = User
                .builder()
                .email(email)
                .name("테스트유저")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .isVerified(true)
                .build();
        entityManager.persist(user);
        return user;
    }

    private Game createGame() {
        Game game
            = Game
                .builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium(stadium)
                .gameAt(LocalDateTime.now().plusDays(7))
                .bookingOpenAt(LocalDateTime.now().minusHours(1))
                .bookingCloseAt(LocalDateTime.now().plusDays(6))
                .bookingStatus(BookingStatus.OPEN)
                .title("테스트 경기")
                .build();
        entityManager.persist(game);
        return game;
    }

    private Ticket createTicket(User user, Game game, TicketStatus targetStatus) {
        fixtureSequence++;
        String seq = String.valueOf(fixtureSequence);

        Seat seat = Seat.of(stadium, zone, "A", "1", seq);
        entityManager.persist(seat);

        GameSeat gameSeat = GameSeat.builder().game(game).seat(seat).price(18_000).build();
        entityManager.persist(gameSeat);

        Reservation reservation
            = Reservation
                .builder()
                .reservationNo("RSV-" + seq)
                .user(user)
                .game(game)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        entityManager.persist(reservation);

        Order order = Order.of("ORD-" + seq, user, reservation, 18_000, LocalDateTime.now().plusMinutes(8));
        entityManager.persist(order);

        OrderItem orderItem = OrderItem.of(order, gameSeat, 18_000);
        entityManager.persist(orderItem);

        Ticket ticket = Ticket.issue("TKT-" + seq, user, orderItem, gameSeat, "QR-" + seq);
        applyStatus(ticket, targetStatus);
        entityManager.persist(ticket);
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
            case REFUNDED -> {
                ticket.requestRefund(TicketCancelReason.USER_REFUND, null);
                ticket.completeRefund();
            }
            case USED_ENTERED -> ticket.markEntered();
            case USED_NO_SHOW -> ticket.markNoShow();
            default -> throw new IllegalArgumentException("지원하지 않는 상태: " + targetStatus);
        }
    }
}
