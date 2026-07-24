package com.backtoback.reseat.domain.reservation.service;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.reservation.dto.SeatHoldRequest;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * [이슈 #172] 락 미적용 over-booking 재현 동시성 테스트.
 * <p>
 * 락 없는 holdSeats()에 N개 스레드가 동일 gameSeatId로 동시 요청 시
 * 성공 건수 > 1 이 발생함을 증명한다. (B2 버그 재현)
 * <p>
 * [이슈 #173] Redisson 분산락 도입 후 이 테스트는 성공 1건으로 전환된다. (회귀 테스트 승격)
 */
@Slf4j
@ActiveProfiles("test-concurrency")
@SpringBootTest
class SeatHoldConcurrencyTest {

    // 재현이 안 되면 늘려라
    private static final int THREAD_COUNT = 10;

    @Autowired private ReservationService reservationService;
    @Autowired private ReservationSeatRepository reservationSeatRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private GameSeatRepository gameSeatRepository;
    @Autowired private GameRepository gameRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatZoneRepository seatZoneRepository;
    @Autowired private StadiumRepository stadiumRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private UserRepository userRepository;

    private Long targetGameSeatId;
    private Long gameId;
    private Long stadiumId;
    private final List<Long> userIds = new ArrayList<>();

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

        Game game = Game.builder()
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

        SeatZone zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, 18000);
        seatZoneRepository.save(zone);

        Seat seat = Seat.of(stadium, zone, "A", "1", "1");
        seatRepository.save(seat);

        GameSeat gameSeat = GameSeat.builder()
            .game(game)
            .seat(seat)
            .price(18000)
            .status(GameSeatStatus.AVAILABLE)
            .build();
        gameSeatRepository.save(gameSeat);
        targetGameSeatId = gameSeat.getId();

        for (int i = 0; i < THREAD_COUNT; i++) {
            User user = User.builder()
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

    // FK 역순 삭제: reservation_seats → reservations → game_seats → seats → seat_zones → games → teams → stadiums → users
    @AfterEach
    void tearDown() {
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();

        if (targetGameSeatId != null) {
            gameSeatRepository.deleteById(targetGameSeatId);
        }
        if (gameId != null) {
            gameRepository.deleteById(gameId);
        }

        seatRepository.deleteAll();
        seatZoneRepository.deleteAll();

        if (stadiumId != null) {
            stadiumRepository.deleteById(stadiumId);
        }
        teamRepository.deleteAll();
        userRepository.deleteAllById(userIds);
        userIds.clear();
    }

    @Test
    @DisplayName("락 미적용 상태에서 N개 스레드 동시 선점 시 성공 건수 > 1 — over-booking 재현")
    void should_allowMultipleSuccess_when_noLockApplied() throws InterruptedException {
        // given
        SeatHoldRequest request = new SeatHoldRequest(gameId, List.of(targetGameSeatId));

        // readyLatch(N→0): 모든 스레드 준비 완료 신호
        // startLatch(1→0): 동시 출발 신호
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
                    // SeatAlreadyHeldException 또는 DataIntegrityViolationException(uk_game_seats_game_seat)
                    failCount.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(finished)
            .as("10초 내에 모든 스레드가 종료되지 않았다 — 데드락 또는 타임아웃 의심")
            .isTrue();

        // then
        long heldCount = reservationSeatRepository.findAll().stream()
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
        log.info("════════════════════════════════════════════════════");

        // 성공 건수 > 1 → over-booking 재현 증명
        // 이슈 #173 이후 successCount == 1 로 전환됨 (회귀 검증)
        assertThat(successCount.get())
            .as("1건이면 우연히 직렬화된 것 — THREAD_COUNT를 늘려 재시도하라.")
            .isGreaterThan(1);

        assertThat(heldCount)
            .as("reservation_seats 중복 행 수는 성공 건수와 일치해야 한다.")
            .isEqualTo(successCount.get());
    }
}
