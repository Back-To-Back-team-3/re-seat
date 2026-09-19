package com.backtoback.reseat.domain.reservation.service.concurrency.pessimistic;

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
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.queue.service.AdmissionTokenService;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationResponse;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.domain.reservation.service.ReservationService;
import com.backtoback.reseat.domain.reservation.service.SeatHoldFacade;
import com.backtoback.reseat.domain.reservation.service.lock.PessimisticLockStrategy;
import com.backtoback.reseat.domain.reservation.service.lock.UserGameLockStrategy;
import com.backtoback.reseat.domain.reservation.service.port.TicketCountPort;
import com.backtoback.reseat.domain.reservation.service.port.UserVerificationPort;
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
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * [이슈 #514] 비관적 락(SELECT FOR UPDATE) 기반 Facade 경로 동시성 테스트.
 * <ul>
 * <li>{@link #should_holdSeatOnce_when_sameSeatRequestsCompeteThroughFacade()} — 동일 좌석 동시 요청, 성공 1건</li>
 * <li>{@link #should_finishWithoutDeadlock_when_multiSeatRequestsCrossOrder()} — 다좌석 교차 순서, 데드락 없음</li>
 * </ul>
 */
@Slf4j
@EnabledIfEnvironmentVariable(
    named = "RUN_CONCURRENCY_TESTS",
    matches = "true"
)
@Tag("concurrency")
@ActiveProfiles("test-concurrency")
@SpringBootTest
class PessimisticLockStrategyConcurrencyTest {

    private static final int THREAD_COUNT = 10;
    private static final int AWAIT_SECONDS = 15;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> tokenIds = new ArrayList<>();

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private PessimisticLockStrategy pessimisticLockStrategy;
    @Autowired
    private AdmissionTokenService admissionTokenService;
    @Autowired
    private TicketCountPort ticketCountPort;
    @Autowired
    private UserVerificationPort userVerificationPort;
    @Autowired
    private UserGameLockStrategy userGameLockStrategy;

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

    private SeatHoldFacade seatHoldFacade;

    private Long targetGameSeatId;
    private Long secondGameSeatId;
    private Long gameId;
    private Long seatId;
    private Long secondSeatId;
    private Long seatZoneId;
    private Long stadiumId;
    private Long homeTeamId;
    private Long awayTeamId;

    @BeforeEach
    void setUp() {
        seatHoldFacade
            = new SeatHoldFacade(
                reservationService,
                pessimisticLockStrategy,
                admissionTokenService,
                reservationSeatRepository,
                ticketCountPort,
                userVerificationPort,
                userGameLockStrategy
            );

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
                .title("[이슈 #514] 비관적 락 동시성 테스트 경기")
                .build();
        gameRepository.save(game);
        gameId = game.getId();

        SeatZone zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, 18000);
        seatZoneRepository.save(zone);
        seatZoneId = zone.getId();

        // 좌석 두 개 준비 — 좌석1은 단일 좌석 경쟁 테스트, 좌석1+2는 교차 순서 데드락 테스트에 사용
        Seat seat = Seat.of(stadium, zone, "A", "1", "1");
        seatRepository.save(seat);
        seatId = seat.getId();

        Seat secondSeat = Seat.of(stadium, zone, "A", "1", "2");
        seatRepository.save(secondSeat);
        secondSeatId = secondSeat.getId();

        GameSeat gameSeat
            = GameSeat.builder().game(game).seat(seat).price(18000).status(GameSeatStatus.AVAILABLE).build();
        gameSeatRepository.save(gameSeat);
        targetGameSeatId = gameSeat.getId();

        GameSeat secondGameSeat
            = GameSeat.builder().game(game).seat(secondSeat).price(18000).status(GameSeatStatus.AVAILABLE).build();
        gameSeatRepository.save(secondGameSeat);
        secondGameSeatId = secondGameSeat.getId();

        // 각 사용자에게 유효한 Queue-Token 사전 발급
        for (int i = 0; i < THREAD_COUNT; i++) {
            User user
                = User
                    .builder()
                    .email("pessimistic-concurrency-" + i + "@reseat.com")
                    .password("pw")
                    .name("테스트유저" + i)
                    .phone("010-2222-" + String.format("%04d", i))
                    .isVerified(true)
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build();
            userRepository.save(user);
            userIds.add(user.getId());

            AdmissionToken token
                = AdmissionToken
                    .of(
                        game,
                        user,
                        "qt_pessimistic-token-" + i,
                        LocalDateTime.now(),
                        LocalDateTime.now().plusMinutes(21),
                        LocalDateTime.now().plusMinutes(3)
                    );
            admissionTokenRepository.save(token);
            tokenIds.add(token.getId());
        }
    }

    @AfterEach
    void tearDown() {
        admissionTokenRepository.deleteAllById(tokenIds);
        tokenIds.clear();

        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();

        gameSeatRepository.deleteAll();
        if (gameId != null) {
            gameRepository.deleteById(gameId);
        }
        if (seatId != null) {
            seatRepository.deleteById(seatId);
        }
        if (secondSeatId != null) {
            seatRepository.deleteById(secondSeatId);
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

        if (!userIds.isEmpty()) {
            userRepository.deleteAllById(userIds);
            userIds.clear();
        }
    }

    /**
     * 동시성 테스트 (단일 좌석 경쟁).
     */
    @Test
    @DisplayName("[이슈 #514] 비관적 락 적용 Facade 경로에서 동일 좌석 N 요청 시 성공 1건, over-booking 0건")
    void should_holdSeatOnce_when_sameSeatRequestsCompeteThroughFacade() throws InterruptedException {
        // given
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger seatAlreadyHeldCount = new AtomicInteger(0);
        AtomicInteger lockFailedCount = new AtomicInteger(0);
        AtomicInteger dataIntegrityViolationCount = new AtomicInteger(0);
        AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long userId = userIds.get(i);
            final String token = "qt_pessimistic-token-" + i;

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    SeatHoldRequest request = new SeatHoldRequest(gameId, List.of(targetGameSeatId));
                    ReservationResponse response = seatHoldFacade.holdSeats(userId, token, request);
                    assertThat(response.status()).isEqualTo(ReservationStatus.HOLDING);
                    successCount.incrementAndGet();
                } catch (com.backtoback.reseat.domain.reservation.exception.SeatAlreadyHeldException e) {
                    seatAlreadyHeldCount.incrementAndGet();
                } catch (com.backtoback.reseat.domain.reservation.exception.LockFailedException e) {
                    lockFailedCount.incrementAndGet();
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    dataIntegrityViolationCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("[이슈 #514] 예상 외 예외 발생: {}", e.getClass().getSimpleName(), e);
                    unexpectedExceptionCount.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS);

        // then
        GameSeat finalGameSeat = gameSeatRepository.findById(targetGameSeatId).orElseThrow();
        long reservationSeatRows
            = reservationSeatRepository
                .findAll()
                .stream()
                .filter(rs -> rs.getGameSeat().getId().equals(targetGameSeatId))
                .count();

        log.info("===================================================");
        log.info("[이슈 #514] 비관적 락 Facade 경로 동시성 검증 수치");
        log.info("  동시 스레드 수                : {}", THREAD_COUNT);
        log.info("  선점 성공 건수                : {}", successCount.get());
        log.info("  SEAT_ALREADY_HELD 건수        : {}", seatAlreadyHeldCount.get());
        log.info("  LOCK_FAILED 건수              : {}", lockFailedCount.get());
        log.info("  DataIntegrityViolation 건수   : {}", dataIntegrityViolationCount.get());
        log.info("  예상 외 예외 건수              : {}", unexpectedExceptionCount.get());
        log.info("  game_seats.status             : {}", finalGameSeat.getStatus());
        log.info("  reservation_seats 행 수        : {}", reservationSeatRows);
        log.info("===================================================");

        assertThat(finished).as("15초 내에 모든 스레드가 종료되지 않았다 — 데드락 또는 타임아웃 의심").isTrue();

        assertThat(successCount.get()).as("비관적 락 적용 후 성공은 정확히 1건이어야 한다").isEqualTo(1);

        assertThat(reservationSeatRows).as("reservation_seats 행은 1건이어야 한다 — over-booking 0건 보장").isEqualTo(1);

        assertThat(finalGameSeat.getStatus()).as("최종 좌석 상태는 HELD여야 한다").isEqualTo(GameSeatStatus.HELD);

        assertThat(dataIntegrityViolationCount.get()).as("DB 유니크 위반은 0건이어야 한다 — 락이 DB까지 도달하는 요청을 차단해야 한다").isZero();

        assertThat(unexpectedExceptionCount.get()).as("예상 외 예외는 0건이어야 한다").isZero();

        assertThat(seatAlreadyHeldCount.get() + lockFailedCount.get())
            .as("실패 건수 합계는 THREAD_COUNT - 1이어야 한다")
            .isEqualTo(THREAD_COUNT - 1);
    }

    /**
     * 동시성 테스트 (다좌석 교차 순서 — 데드락 회귀 검증).
     * <p>서로 다른 두 사용자가 같은 두 좌석을 서로 반대 순서로 동시에 요청한다(A → [seat1, seat2], B → [seat2, seat1]).
     * {@code PessimisticLockStrategy}가 내부적으로 gameSeatIds를 오름차순 정렬한 뒤 락을 획득하지 않으면 순환 대기(데드락)가 발생할 수 있다.
     * <p>결과 상태를 정밀 검증하지 않고 {@code AWAIT_SECONDS} 내 정상 종료 여부만 확인한다.
     */
    @Test
    @DisplayName("[이슈 #514] 다좌석 교차 순서 요청 시 15초 내 종료 — 데드락 없음")
    void should_finishWithoutDeadlock_when_multiSeatRequestsCrossOrder() throws InterruptedException {
        // given — 4명 중 2명은 [seat1, seat2] 순서, 나머지 2명은 [seat2, seat1] 역순으로 요청
        int crossOrderThreadCount = 4;
        CountDownLatch readyLatch = new CountDownLatch(crossOrderThreadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(crossOrderThreadCount);

        // when
        for (int i = 0; i < crossOrderThreadCount; i++) {
            final Long userId = userIds.get(i);
            final String token = "qt_pessimistic-token-" + i;
            final boolean reversedOrder = (i % 2 == 1);
            final List<Long> seatOrder
                = reversedOrder ? List.of(secondGameSeatId, targetGameSeatId)
                    : List.of(targetGameSeatId, secondGameSeatId);

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    SeatHoldRequest request = new SeatHoldRequest(gameId, seatOrder);
                    seatHoldFacade.holdSeats(userId, token, request);
                } catch (com.backtoback.reseat.domain.reservation.exception.SeatAlreadyHeldException
                    | com.backtoback.reseat.domain.reservation.exception.LockFailedException e) {
                    // 경쟁에서 밀린 정상적인 비즈니스 실패
                } catch (Exception e) {
                    log.error("[이슈 #514] 교차 순서 요청 중 예상 외 예외: {}", e.getClass().getSimpleName(), e);
                    unexpectedExceptionCount.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS);

        // then
        assertThat(finished).as("%d초 내에 모든 스레드가 종료되지 않았다 — 오름차순 락 획득 규칙이 깨져 데드락이 발생했을 가능성이 있다", AWAIT_SECONDS).isTrue();

        assertThat(unexpectedExceptionCount.get()).as("예상 외 예외는 0건이어야 한다").isZero();
    }
}
