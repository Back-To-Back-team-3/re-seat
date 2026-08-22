package com.backtoback.reseat.domain.reservation.service;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
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
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사용자·경기 단위 락 동시성 회귀 테스트.
 * <p> SeatHoldFacadeConcurrencyTest가 "동일 좌석" 경합을 검증하는 것과 달리,
 * 이 테스트는 "동일 사용자, 서로 다른 좌석" 경합을 검증한다.</p>
 * <p> 핵심 검증:
 * - 동일 사용자가 서로 다른 좌석 3개에 동시 요청 → 성공 2건(상한), 1건 MAX_SEAT_COUNT_EXCEEDED
 * - reservation_seats 행 2건 (over-booking 0건 — 4좌석 등 상한 초과 불가)
 * - DB 유니크 위반 0건
 * - 예상 외 예외 0건
 * </p>
 */
@Disabled("테스트 제외")
@Slf4j
@EnabledIfEnvironmentVariable(
    named = "RUN_CONCURRENCY_TESTS",
    matches = "true"
)
@Tag("concurrency")
@ActiveProfiles("test-concurrency")
@SpringBootTest
class SeatHoldFacadeUserLockConcurrencyTest {

    // 상한(2매)을 초과하는 3좌석을 동시 요청해 경계값을 검증한다.
    private static final int SEAT_COUNT = 3;
    private static final int EXPECTED_SUCCESS_COUNT = 2;

    private final List<Long> gameSeatIds = new ArrayList<>();

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

    private Long userId;
    private Long gameId;
    private Long seatZoneId;
    private Long stadiumId;
    private Long homeTeamId;
    private Long awayTeamId;
    private Long tokenId;
    private String token;
    private final List<Long> seatIds = new ArrayList<>();

    // @Transactional 금지 → @AfterEach 수동 정리
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
                .title("[C-9-3] 사용자·경기 단위 락 동시성 테스트 경기")
                .build();
        gameRepository.save(game);
        gameId = game.getId();

        SeatZone zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, 18000);
        seatZoneRepository.save(zone);
        seatZoneId = zone.getId();

        // 좌석 3개 생성 — 동일 사용자가 이 3개를 동시에 요청한다.
        for (int i = 0; i < SEAT_COUNT; i++) {
            Seat seat = Seat.of(stadium, zone, "A", "1", String.valueOf(i + 1));
            seatRepository.save(seat);
            seatIds.add(seat.getId());

            GameSeat gameSeat
                = GameSeat.builder().game(game).seat(seat).price(18000).status(GameSeatStatus.AVAILABLE).build();
            gameSeatRepository.save(gameSeat);
            gameSeatIds.add(gameSeat.getId());
        }

        // 사용자는 1명만 생성 — 이 테스트의 핵심 조건.
        User user
            = User
                .builder()
                .email("user-lock-concurrency@reseat.com")
                .password("pw")
                .name("테스트유저")
                .phone("010-2222-0000")
                .isVerified(true)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);
        userId = user.getId();

        // 동일 토큰으로 3개 요청을 동시에 보낸다 — validateToken은 소비하지 않으므로 재사용 가능.
        token = "qt_user-lock-concurrency";
        AdmissionToken admissionToken
            = AdmissionToken
                .of(
                    game,
                    user,
                    token,
                    LocalDateTime.now(),
                    LocalDateTime.now().plusMinutes(21),
                    LocalDateTime.now().plusMinutes(3)
                );
        admissionTokenRepository.save(admissionToken);
        tokenId = admissionToken.getId();
    }

    @AfterEach
    void tearDown() {
        if (tokenId != null)
            admissionTokenRepository.deleteById(tokenId);

        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();

        gameSeatRepository.deleteAll();
        for (Long seatId : seatIds) {
            seatRepository.deleteById(seatId);
        }
        if (gameId != null)
            gameRepository.deleteById(gameId);
        if (seatZoneId != null)
            seatZoneRepository.deleteById(seatZoneId);
        if (homeTeamId != null)
            teamRepository.deleteById(homeTeamId);
        if (awayTeamId != null)
            teamRepository.deleteById(awayTeamId);
        if (stadiumId != null)
            stadiumRepository.deleteById(stadiumId);
        if (userId != null)
            userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("동일 사용자가 서로 다른 좌석 3개를 동시 요청하면 성공은 2건, 초과 1건은 400이다")
    void should_limitToTwoSuccesses_when_sameUserRequestsThreeDifferentSeatsConcurrently() throws InterruptedException {
        // given
        CountDownLatch readyLatch = new CountDownLatch(SEAT_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger maxSeatCountExceededCount = new AtomicInteger(0);
        AtomicInteger lockFailedCount = new AtomicInteger(0);
        AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(SEAT_COUNT);

        // when: 동일 userId·동일 token으로 서로 다른 좌석 3개를 동시에 요청한다.
        for (Long gameSeatId : gameSeatIds) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    SeatHoldRequest request = new SeatHoldRequest(gameId, List.of(gameSeatId));
                    seatHoldFacade.holdSeats(userId, token, request);
                    successCount.incrementAndGet();
                } catch (MaxSeatCountExceededException e) {
                    maxSeatCountExceededCount.incrementAndGet();
                } catch (LockFailedException e) {
                    lockFailedCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("예상 외 예외 발생: {}", e.getClass().getSimpleName(), e);
                    unexpectedExceptionCount.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);

        // then
        long reservationSeatRows
            = reservationSeatRepository
                .findAll()
                .stream()
                .filter(rs -> gameSeatIds.contains(rs.getGameSeat().getId()))
                .count();

        log.info("==================================================");
        log.info("[이슈 #322] 사용자·경기 단위 락 동시성 검증 수치");
        log.info("  동시 요청 좌석 수              : {}", SEAT_COUNT);
        log.info("  선점 성공 건수                 : {}", successCount.get());
        log.info("  MAX_SEAT_COUNT_EXCEEDED 건수   : {}", maxSeatCountExceededCount.get());
        log.info("  LOCK_FAILED 건수               : {}", lockFailedCount.get());
        log.info("  예상 외 예외 건수              : {}", unexpectedExceptionCount.get());
        log.info("  reservation_seats 행 수        : {}", reservationSeatRows);
        log.info("==================================================");

        assertThat(finished).as("15초 내에 모든 스레드가 종료되지 않았다 — 데드락 또는 타임아웃 의심").isTrue();

        // 사용자 락이 없다면 검증 시점에 heldSeatCount=0으로 3건 모두 통과할 수 있다(over-booking).
        // 이 테스트의 핵심 assertion: 상한(2매)을 초과하는 성공은 발생하지 않는다.
        assertThat(successCount.get())
            .as("사용자·경기 단위 락으로 누적 상한(%d매)을 초과하는 성공은 발생하지 않아야 한다", EXPECTED_SUCCESS_COUNT)
            .isEqualTo(EXPECTED_SUCCESS_COUNT);

        assertThat(reservationSeatRows)
            .as("reservation_seats 행은 상한과 같아야 한다 — over-booking 0건 보장")
            .isEqualTo(EXPECTED_SUCCESS_COUNT);

        assertThat(maxSeatCountExceededCount.get())
            .as("상한 초과분은 정확히 1건이어야 한다")
            .isEqualTo(SEAT_COUNT - EXPECTED_SUCCESS_COUNT);

        assertThat(unexpectedExceptionCount.get()).as("예상 외 예외는 0건이어야 한다").isZero();
    }
}
