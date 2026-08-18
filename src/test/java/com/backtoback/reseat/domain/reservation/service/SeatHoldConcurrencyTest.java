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
import org.junit.jupiter.api.Disabled;
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
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
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

/**
 * [이슈 #172] 락 미적용 over-booking 재현 동시성 테스트.
 * <p>
 * 락 없는 holdSeats()에 N개 스레드가 동일 gameSeatId로 동시 요청 시
 * 성공 건수 > 1 이 발생함을 증명한다. (B2 버그 재현)
 * <p>
 * [이슈 #173] Redisson 분산락 도입 후 이 테스트는 성공 1건으로 전환된다. (회귀 테스트 승격)
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
class SeatHoldConcurrencyTest {

    // 재현이 안 될 경우 THREAD_COUNT를 20~50으로 올려서 재시도한다.
    private static final int THREAD_COUNT = 10;
    private final List<Long> userIds = new ArrayList<>();
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationSeatRepository reservationSeatRepository;
    @Autowired
    private ReservationRepository reservationRepository;
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
    // 테스트 픽스처 ID — @AfterEach 수동 정리에 사용
    private Long targetGameSeatId;
    private Long gameId;
    private Long seatId;
    private Long seatZoneId;
    private Long stadiumId;
    private Long homeTeamId;
    private Long awayTeamId;

    // @Transactional 금지 → @AfterEach 수동 정리
    @BeforeEach
    void setUp() {
        // Stadium
        Stadium stadium = Stadium.of("테스트 구장", "서울시 테스트구 1", 10000);
        stadiumRepository.save(stadium);
        stadiumId = stadium.getId();

        // Team
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
                .title("[이슈 #172] 동시성 테스트 경기")
                .build();
        gameRepository.save(game);
        gameId = game.getId();

        // SeatZone + Seat
        SeatZone zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, 18000);
        seatZoneRepository.save(zone);
        seatZoneId = zone.getId();

        Seat seat = Seat.of(stadium, zone, "A", "1", "1");
        seatRepository.save(seat);
        seatId = seat.getId();

        // GameSeat (테스트 대상 — AVAILABLE 상태 1건)
        GameSeat gameSeat
            = GameSeat.builder().game(game).seat(seat).price(18000).status(GameSeatStatus.AVAILABLE).build();
        gameSeatRepository.save(gameSeat);
        targetGameSeatId = gameSeat.getId();

        // User × THREAD_COUNT (각 스레드가 서로 다른 사용자로 요청)
        for (int i = 0; i < THREAD_COUNT; i++) {
            User user
                = User
                    .builder()
                    .email("concurrency-test-" + i + "@reseat.com")
                    .password("pw")
                    .name("테스트유저" + i)
                    .phone("010-0000-" + String.format("%04d", i))
                    .isVerified(true)
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build();
            userRepository.save(user);
            userIds.add(user.getId());
        }
    }

    /**
     * FK 역참조 역순 삭제 순서:
     * reservation_seats → reservations
     * → game_seats (game 참조) → games (team, stadium 참조)
     * → seats (seat_zone 참조) → seat_zones (stadium 참조)
     * → teams (stadium 참조) → stadiums
     * → users
     */
    @AfterEach
    void tearDown() {
        // 예약 관련 (reservation_seats → reservations)
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();

        // game_seats 전체 삭제 후 game 삭제 (FK 순서 준수)
        gameSeatRepository.deleteAll();
        if (gameId != null) {
            gameRepository.deleteById(gameId);
        }

        // seats → seat_zones (FK 순서 준수)
        if (seatId != null) {
            seatRepository.deleteById(seatId);
        }
        if (seatZoneId != null) {
            seatZoneRepository.deleteById(seatZoneId);
        }

        // teams → stadiums
        if (homeTeamId != null) {
            teamRepository.deleteById(homeTeamId);
        }
        if (awayTeamId != null) {
            teamRepository.deleteById(awayTeamId);
        }
        if (stadiumId != null) {
            stadiumRepository.deleteById(stadiumId);
        }

        // users
        if (!userIds.isEmpty()) {
            userRepository.deleteAllById(userIds);
            userIds.clear();
        }
    }

    @Test
    @DisplayName("[B2 over-booking 재현] 락 미적용 상태에서 N개 스레드 동시 선점 시 성공 건수 > 1")
    void should_allowMultipleSuccess_when_noLockApplied() throws InterruptedException {
        // given
        SeatHoldRequest request = new SeatHoldRequest(gameId, List.of(targetGameSeatId));

        /**
         * CountDownLatch 2개 패턴
         * readyLatch(N→0): 모든 스레드 준비 완료 신호
         * startLatch(1→0): 동시 출발 신호
         */
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long userId = userIds.get(i);
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    reservationService.holdSeats(userId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    /*
                     * 예상 예외 두 가지:
                     * 1. SeatAlreadyHeldException (SEAT_ALREADY_HELD, 409)
                     *    — 서비스 레벨에서 상태 체크 후 거부
                     * 2. DataIntegrityViolationException
                     *    — uk_game_seats_game_seat 유니크 제약이 최후 방어선으로 작동
                     *    — 이 경우도 사용자에게 500이 아니라 409로 변환 필요
                     */
                    failCount.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);

        // then
        assertThat(finished).as("10초 내에 모든 스레드가 종료되지 않았다 — 데드락 또는 타임아웃 의심").isTrue();

        long heldCount
            = reservationSeatRepository
                .findAll()
                .stream()
                .filter(rs -> rs.getGameSeat().getId().equals(targetGameSeatId))
                .count();

        GameSeat finalGameSeat = gameSeatRepository.findById(targetGameSeatId).orElseThrow();

        log.info("════════════════════════════════════════════════════");
        log.info("[이슈 #172] 락 미적용 over-booking 재현 수치");
        log.info("  동시 스레드 수              : {}", THREAD_COUNT);
        log.info("  선점 성공 건수              : {}", successCount.get());
        log.info("  선점 실패 건수              : {}", failCount.get());
        log.info("  game_seats.status         : {}", finalGameSeat.getStatus());
        log.info("  reservation_seats 중복 행 수: {}", heldCount);
        log.info("  ※ 성공 1건 = 우연히 직렬화됨. THREAD_COUNT를 올려 재시도하기.");
        log.info("════════════════════════════════════════════════════");

        // [이슈 #172] 성공 건수 > 1 → over-booking 재현 증명
        // [이슈 #173] Redisson 분산락 머지 후 isEqualTo(1)로 전환된다. (회귀 검증)
        assertThat(successCount.get() + failCount.get()).as("모든 스레드가 경합에 참여해야 한다.").isEqualTo(THREAD_COUNT);

        assertThat(successCount.get())
            .as("성공 건수가 1이면 우연히 직렬화된 것. over-booking 미재현 — THREAD_COUNT를 늘려서 재시도.")
            .isEqualTo(1);

        assertThat(heldCount).as("reservation_seats 중복 행 수는 성공 건수와 일치해야 한다.").isEqualTo(1);
    }
}
