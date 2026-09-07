package com.backtoback.reseat.domain.ticket.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
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
import com.backtoback.reseat.global.config.JpaConfig;
import com.backtoback.reseat.global.config.QuerydslConfig;

import jakarta.persistence.EntityManager;

/**
 * TicketRepository의 사용자·경기별 활성 티켓 수 집계 쿼리를 검증한다.
 * <p>ISSUED·REFUND_PENDING·REFUND_FAILED만 카운트되고, 그 외 상태는 제외되는지 확인한다.
 */
@DataJpaTest
@Import(
    {
        QuerydslConfig.class,
        JpaConfig.class
    }
)
class TicketRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TicketRepository ticketRepository;

    private Stadium stadium;
    private Team homeTeam;
    private Team awayTeam;
    private SeatZone zone;
    private long fixtureSequence;

    /**
     * 세 테스트가 공유해도 무방한 기준 데이터(구장·구단·구역)만 한 번 저장한다.
     * 경기·사용자는 테스트별 격리가 필요하므로 각 테스트에서 별도로 만든다.
     */
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
    @DisplayName("should_countIssuedRefundPendingRefundFailed_when_activeTicketsExist")
    void should_countIssuedRefundPendingRefundFailed_when_activeTicketsExist() {
        // given: 동일 사용자·경기에 ISSUED 1건, REFUND_PENDING 1건, REFUNDED 1건을 생성한다.
        User user = createUser("user1@test.com");
        Game game = createGame();
        createTicket(user, game, TicketStatus.ISSUED);
        createTicket(user, game, TicketStatus.REFUND_PENDING);
        createTicket(user, game, TicketStatus.REFUNDED);

        // when
        int count
            = ticketRepository
                .countByUserIdAndGameIdAndStatusIn(
                    user.getId(),
                    game.getId(),
                    List.of(TicketStatus.ISSUED, TicketStatus.REFUND_PENDING, TicketStatus.REFUND_FAILED)
                );

        // then: REFUNDED는 제외되어 2건만 카운트된다.
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("should_excludeRefundedUsedEnteredUsedNoShow_when_counting")
    void should_excludeRefundedUsedEnteredUsedNoShow_when_counting() {
        // given: 환불 완료·입장 완료·미입장 처리 티켓만 3건 보유
        User user = createUser("user2@test.com");
        Game game = createGame();
        createTicket(user, game, TicketStatus.REFUNDED);
        createTicket(user, game, TicketStatus.USED_ENTERED);
        createTicket(user, game, TicketStatus.USED_NO_SHOW);

        // when
        int count
            = ticketRepository
                .countByUserIdAndGameIdAndStatusIn(
                    user.getId(),
                    game.getId(),
                    List.of(TicketStatus.ISSUED, TicketStatus.REFUND_PENDING, TicketStatus.REFUND_FAILED)
                );

        // then: 셋 다 대상 상태가 아니므로 0건
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("should_excludeOtherUserOrOtherGame_when_counting")
    void should_excludeOtherUserOrOtherGame_when_counting() {
        // given: 대상 사용자·경기에는 ISSUED 1건만, 다른 사용자·다른 경기에는 각각 ISSUED 1건씩
        User targetUser = createUser("target@test.com");
        User otherUser = createUser("other@test.com");
        Game targetGame = createGame();
        Game otherGame = createGame();

        createTicket(targetUser, targetGame, TicketStatus.ISSUED);
        createTicket(otherUser, targetGame, TicketStatus.ISSUED); // 다른 사용자, 같은 경기
        createTicket(targetUser, otherGame, TicketStatus.ISSUED); // 같은 사용자, 다른 경기

        // when
        int count
            = ticketRepository
                .countByUserIdAndGameIdAndStatusIn(
                    targetUser.getId(),
                    targetGame.getId(),
                    List.of(TicketStatus.ISSUED, TicketStatus.REFUND_PENDING, TicketStatus.REFUND_FAILED)
                );

        // then: 대상 사용자·경기 조합 1건만 카운트된다.
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

    /**
     * 주어진 사용자·경기에 특정 상태의 티켓 1장을 만들어 저장한다.
     * GameSeat·Reservation·Order·OrderItem은 티켓과 1:1 관계라 매번 새로 만든다.
     */
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

    /**
     * ISSUED로 발급된 티켓을 목표 상태까지 도메인 메서드로 전이시킨다.
     */
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
