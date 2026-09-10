package com.backtoback.reseat.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderItem;
import com.backtoback.reseat.domain.order.repository.OrderItemRepository;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.exception.LockFailedException;
import com.backtoback.reseat.domain.reservation.exception.MaxSeatCountExceededException;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
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
import com.backtoback.reseat.domain.ticket.repository.TicketRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * [이슈 #382] REFUND_PENDING 구간 포함 수량 초과 동시 재선점 차단 검증.
 * <p>PR #451의 RefundPendingQuantityGuardTest(순차 요청 기준)를 동시 요청으로 확장한다.
 * 사용자가 이미 ISSUED 1매 + REFUND_PENDING 1매(= 정책상 2매 보유)인 상태에서
 * 서로 다른 좌석에 대한 추가 선점 요청 N건을 동시에 보내면 전부 MAX_SEAT_COUNT_EXCEEDED로 차단되어야 한다.
 * 좌석은 서로 달라 좌석 락 경합은 없고, userGameLockStrategy가 수량 검증 구간을 원자적으로 직렬화하는지가 검증 대상이다.</p>
 * <p>HOLDING 예약 기반 집계와 분리하기 위해, 기존 보유분은 CONFIRMED 예약 + 발급된 티켓으로만 구성한다(HOLDING 예약 없음).</p>
 */
// @Disabled("테스트 제외")
@Slf4j
@EnabledIfEnvironmentVariable(
    named = "RUN_CONCURRENCY_TESTS",
    matches = "true"
)
@Tag("concurrency")
@ActiveProfiles("test-concurrency")
@SpringBootTest
class RefundPendingQuantityGuardConcurrencyTest {

    private static final int THREAD_COUNT = 20;
    private static final int AWAIT_SECONDS = 20;

    private final List<Long> candidateSeatIds = new ArrayList<>();

    @Autowired
    private SeatHoldFacade seatHoldFacade;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private ReservationSeatRepository reservationSeatRepository;
    @Autowired
    private GameSeatRepository gameSeatRepository;
    @Autowired
    private GameRepository gameRepository;
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
    private AdmissionTokenRepository admissionTokenRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private TicketRepository ticketRepository;

    private Long userId;
    private Long gameId;
    private Long seatZoneId;
    private Long stadiumId;
    private Long homeTeamId;
    private Long awayTeamId;
    private final List<Long> ownedSeatPhysicalIds = new ArrayList<>();
    private final List<Long> candidatePhysicalSeatIds = new ArrayList<>();
    private Long orderId;
    private final List<Long> orderItemIds = new ArrayList<>();
    private final List<Long> ticketIds = new ArrayList<>();
    private Long admissionTokenId;

    @BeforeEach
    void setUp() {
        Stadium stadium = Stadium.of("테스트 구장", "서울시 테스트구 1", 10000);
        stadiumRepository.save(stadium);
        stadiumId = stadium.getId();

        Team homeTeam = Team.of("홈팀", stadium);
        Team awayTeam = Team.of("원정팀", stadium);
        teamRepository.save(homeTeam);
        teamRepository.save(awayTeam);
        homeTeamId = homeTeam.getId();
        awayTeamId = awayTeam.getId();

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
                .title("[이슈 #382] REFUND_PENDING 수량 동시성 테스트 경기")
                .build();
        gameRepository.save(game);
        gameId = game.getId();

        SeatZone zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, 18000);
        seatZoneRepository.save(zone);
        seatZoneId = zone.getId();

        User user
            = User
                .builder()
                .email("refund-pending-guard@reseat.com")
                .password("pw")
                .name("환불대기수량테스트유저")
                .phone("010-6666-0000")
                .isVerified(true)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);
        userId = user.getId();

        // 1. 이미 보유한 2매를 구성한다: ISSUED 1매 + REFUND_PENDING 1매
        GameSeat issuedGameSeat = createSoldGameSeat(game, zone, "1");
        GameSeat refundPendingGameSeat = createSoldGameSeat(game, zone, "2");

        // HOLDING 예약 카운트와 분리하기 위해 CONFIRMED로 둔다.
        Reservation confirmedReservation
            = Reservation
                .builder()
                .user(user)
                .game(game)
                .reservationNo("RSV-382-QUANTITY-GUARD")
                .status(ReservationStatus.HOLDING)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        reservationRepository.save(confirmedReservation);
        confirmedReservation.confirm();
        reservationRepository.save(confirmedReservation);

        Order order
            = Order
                .of(
                    "ORD-382-QUANTITY-GUARD",
                    user,
                    confirmedReservation,
                    18000 * 2,
                    LocalDateTime.now().plusMinutes(8)
                );
        orderRepository.save(order);
        orderId = order.getId();

        OrderItem issuedOrderItem = OrderItem.of(order, issuedGameSeat, 18000);
        OrderItem refundPendingOrderItem = OrderItem.of(order, refundPendingGameSeat, 18000);
        orderItemRepository.save(issuedOrderItem);
        orderItemRepository.save(refundPendingOrderItem);
        orderItemIds.add(issuedOrderItem.getId());
        orderItemIds.add(refundPendingOrderItem.getId());

        Ticket issuedTicket = Ticket.issue("TKT-382-ISSUED", user, issuedOrderItem, issuedGameSeat, "qr-382-issued");
        Ticket refundPendingTicket
            = Ticket
                .issue(
                    "TKT-382-REFUND-PENDING",
                    user,
                    refundPendingOrderItem,
                    refundPendingGameSeat,
                    "qr-382-refund-pending"
                );
        refundPendingTicket.requestRefund(TicketCancelReason.USER_REFUND, null);
        ticketRepository.save(issuedTicket);
        ticketRepository.save(refundPendingTicket);
        ticketIds.add(issuedTicket.getId());
        ticketIds.add(refundPendingTicket.getId());

        // 2. 추가 선점을 시도할 AVAILABLE 좌석 THREAD_COUNT개 준비 (좌석 락 경합 배제 목적)
        for (int i = 0; i < THREAD_COUNT; i++) {
            Seat seat = Seat.of(stadium, zone, "B", String.valueOf(i / 10 + 1), String.valueOf(i % 10 + 1));
            seatRepository.save(seat);
            candidatePhysicalSeatIds.add(seat.getId());

            GameSeat gameSeat
                = GameSeat.builder().game(game).seat(seat).price(18000).status(GameSeatStatus.AVAILABLE).build();
            gameSeatRepository.save(gameSeat);
            candidateSeatIds.add(gameSeat.getId());
        }

        // 3. 동일 사용자의 유효 Queue-Token 1개 (동시 요청 전부 같은 토큰 사용)
        LocalDateTime issuedAt = LocalDateTime.now();
        AdmissionToken token
            = AdmissionToken
                .of(game, user, "qt-382-quantity-guard", issuedAt, issuedAt.plusMinutes(21), issuedAt.plusMinutes(3));
        admissionTokenRepository.save(token);
        admissionTokenId = token.getId();
    }

    private GameSeat createSoldGameSeat(Game game, SeatZone zone, String number) {
        Seat seat = Seat.of(zone.getStadium(), zone, "A", "1", number);
        seatRepository.save(seat);
        ownedSeatPhysicalIds.add(seat.getId());

        GameSeat gameSeat
            = GameSeat.builder().game(game).seat(seat).price(18000).status(GameSeatStatus.AVAILABLE).build();
        gameSeatRepository.save(gameSeat);
        gameSeat.hold(LocalDateTime.now().plusMinutes(10));
        gameSeat.sell();
        gameSeatRepository.save(gameSeat);
        return gameSeat;
    }

    @AfterEach
    void tearDown() {
        if (admissionTokenId != null) {
            admissionTokenRepository.deleteById(admissionTokenId);
        }
        ticketRepository.deleteAllById(ticketIds);
        orderItemRepository.deleteAllById(orderItemIds);
        if (orderId != null) {
            orderRepository.deleteById(orderId);
        }
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        gameSeatRepository.deleteAll();
        seatRepository.deleteAllById(ownedSeatPhysicalIds);
        seatRepository.deleteAllById(candidatePhysicalSeatIds);
        if (gameId != null) {
            gameRepository.deleteById(gameId);
        }
        if (seatZoneId != null) {
            seatZoneRepository.deleteById(seatZoneId);
        }
        if (homeTeamId != null) {
            teamRepository.deleteById(homeTeamId);
        }
        if (awayTeamId != null) {
            teamRepository.deleteById(awayTeamId);
        }
        if (stadiumId != null) {
            stadiumRepository.deleteById(stadiumId);
        }
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    @DisplayName("[이슈 #382] ISSUED+REFUND_PENDING 2매 보유 상태에서 동시 추가 선점 요청은 전부 차단된다")
    void should_blockAllAttempts_when_userAtQuotaWithRefundPendingTicketRequestsConcurrently()
        throws InterruptedException {
        // given: setUp()에서 이미 사용자는 2매(ISSUED+REFUND_PENDING) 보유 상태
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger maxSeatExceededCount = new AtomicInteger(0);
        AtomicInteger lockFailedCount = new AtomicInteger(0);
        AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

        // when: 서로 다른 좌석에 대해 동일 사용자가 동시에 추가 선점 시도
        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long candidateSeatId = candidateSeatIds.get(i);

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    seatHoldFacade
                        .holdSeats(
                            userId,
                            "qt-382-quantity-guard",
                            new SeatHoldRequest(gameId, List.of(candidateSeatId))
                        );
                    successCount.incrementAndGet();
                } catch (MaxSeatCountExceededException e) {
                    maxSeatExceededCount.incrementAndGet();
                } catch (LockFailedException e) {
                    lockFailedCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("[이슈 #382] 예상 외 예외 발생: {}", e.getClass().getSimpleName(), e);
                    unexpectedExceptionCount.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS);

        // then
        int finalTicketCount
            = ticketRepository
                .countByUserIdAndGameIdAndStatusIn(
                    userId,
                    gameId,
                    List
                        .of(
                            com.backtoback.reseat.domain.ticket.entity.TicketStatus.ISSUED,
                            com.backtoback.reseat.domain.ticket.entity.TicketStatus.REFUND_PENDING,
                            com.backtoback.reseat.domain.ticket.entity.TicketStatus.REFUND_FAILED
                        )
                );

        log.info("=============================================================");
        log.info("[이슈 #382] REFUND_PENDING 구간 동시 수량 초과 차단 검증 수치");
        log.info("  동시 스레드 수                : {}", THREAD_COUNT);
        log.info("  선점 성공 건수(있으면 안 됨)  : {}", successCount.get());
        log.info("  MAX_SEAT_COUNT_EXCEEDED 건수  : {}", maxSeatExceededCount.get());
        log.info("  LOCK_FAILED 건수              : {}", lockFailedCount.get());
        log.info("  예상 외 예외 건수              : {}", unexpectedExceptionCount.get());
        log.info("  집계 대상 티켓 수(불변 확인)   : {}", finalTicketCount);
        log.info("=============================================================");

        assertThat(finished).as("%d초 내에 모든 스레드가 종료되지 않았다 — 데드락 의심", AWAIT_SECONDS).isTrue();

        // 완료 기준 핵심: 단 1건도 통과하면 안 된다.
        assertThat(successCount.get()).as("REFUND_PENDING 포함 2매 보유 상태에서 추가 선점 성공은 0건이어야 한다").isZero();
        assertThat(maxSeatExceededCount.get())
            .as("전부 MAX_SEAT_COUNT_EXCEEDED로 차단되어야 한다")
            .isEqualTo(THREAD_COUNT - lockFailedCount.get());
        assertThat(unexpectedExceptionCount.get()).as("예상 외 예외는 0건이어야 한다").isZero();

        // 이번 시나리오는 좌석 경합이 아니라 수량 검증만 관찰하므로 티켓 수 자체는 불변이어야 한다.
        assertThat(finalTicketCount).as("기존 보유 티켓 수(2)는 변하지 않아야 한다").isEqualTo(2);
    }
}
