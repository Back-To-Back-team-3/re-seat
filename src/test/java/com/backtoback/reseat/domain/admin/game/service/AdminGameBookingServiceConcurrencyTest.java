package com.backtoback.reseat.domain.admin.game.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
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
import com.backtoback.reseat.domain.game.exception.InvalidBookingStatusTransitionException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;
import com.backtoback.reseat.domain.seatinventory.service.GameSeatCreateService;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.stadium.repository.SeatRepository;
import com.backtoback.reseat.domain.stadium.repository.SeatZoneRepository;
import com.backtoback.reseat.domain.stadium.repository.StadiumRepository;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.team.repository.TeamRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * [이슈 #375] AdminGameBookingService.transition() 서비스 계층 경합 테스트.
 * <p>실제 관리자 API 진입점(서비스 메서드)을 동시 호출했을 때도 성공 1건·나머지 409(예외)로 정확히 갈리는지 증명한다.
 * <p>@Transactional을 쓰지 않고 @AfterEach로 직접 데이터를 지운다.
 */
@EnabledIfEnvironmentVariable(
    named = "RUN_CONCURRENCY_TESTS",
    matches = "true"
)
@Tag("concurrency")
@ActiveProfiles("test-concurrency")
@SpringBootTest
@Slf4j
class AdminGameBookingServiceConcurrencyTest {

    private static final int THREAD_COUNT = 2;

    @Autowired
    private AdminGameBookingService adminGameBookingService;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private GameSeatRepository gameSeatRepository;
    @Autowired
    private GameSeatCreateService gameSeatCreateService;
    @Autowired
    private StadiumRepository stadiumRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private SeatZoneRepository seatZoneRepository;
    @Autowired
    private SeatRepository seatRepository;

    private Long gameId;
    private Long stadiumId;
    private Long homeTeamId;
    private Long awayTeamId;
    private Long seatZoneId;
    private Long seatId;

    @BeforeEach
    void setUp() {
        Stadium stadium = stadiumRepository.save(Stadium.of("동시성 테스트 구장", "서울시 테스트구 2", 10_000));
        stadiumId = stadium.getId();

        SeatZone zone = seatZoneRepository.save(SeatZone.of(stadium, "테스트 구역", SeatGrade.INFIELD, 18_000));
        seatZoneId = zone.getId();

        Seat seat = seatRepository.save(Seat.of(stadium, zone, "A", "1", "1"));
        seatId = seat.getId();

        Team homeTeam = teamRepository.save(Team.of("홈팀", stadium));
        Team awayTeam = teamRepository.save(Team.of("원정팀", stadium));
        homeTeamId = homeTeam.getId();
        awayTeamId = awayTeam.getId();

        Game game = Game
            .builder()
            .homeTeam(homeTeam)
            .awayTeam(awayTeam)
            .stadium(stadium)
            .gameAt(LocalDateTime.now().plusDays(7))
            .bookingOpenAt(LocalDateTime.now().minusHours(1))
            .bookingCloseAt(LocalDateTime.now().plusDays(6))
            .bookingStatus(BookingStatus.SCHEDULED)
            .title("[이슈 #375] 서비스 계층 동시성 테스트")
            .build();
        gameRepository.save(game);
        gameId = game.getId();

        // OPEN 전이의 선행 조건인 좌석 재고를 미리 오픈해 둔다.
        gameSeatCreateService.openInventory(gameId);
    }

    /**
     * FK 역참조 역순 삭제 순서: games(team, stadium 참조) → teams(stadium 참조) → stadiums
     */
    @AfterEach
    void tearDown() {
        gameSeatRepository.deleteAllByGameId(gameId);
        gameRepository.deleteById(gameId);
        seatRepository.deleteById(seatId);
        teamRepository.deleteById(homeTeamId);
        teamRepository.deleteById(awayTeamId);
        seatZoneRepository.deleteById(seatZoneId);
        stadiumRepository.deleteById(stadiumId);
    }

    @Test
    @DisplayName("관리자 2명이 AdminGameBookingService.transition()으로 동시 OPEN 요청하면 1건만 성공하고 나머지는 409다")
    void should_succeedOnce_when_twoAdminsCallTransitionToOpenConcurrently() throws InterruptedException {
        // given
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    adminGameBookingService.transition(gameId, BookingStatus.OPEN, "동시성 테스트 — 예매 오픈");
                    successCount.incrementAndGet();
                } catch (InvalidBookingStatusTransitionException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("예상치 못한 예외 발생", e);
                    unexpectedErrorCount.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);

        // then
        assertThat(finished).as("10초 내에 모든 스레드가 종료되지 않았다 — 데드락 또는 타임아웃 의심").isTrue();

        Game reloaded = gameRepository.findById(gameId).orElseThrow();

        log.info("============================================================");
        log.info("[이슈 #375] AdminGameBookingService OPEN 동시성 테스트 수치");
        log.info("  동시 스레드 수        : {}", THREAD_COUNT);
        log.info("  성공 건수             : {}", successCount.get());
        log.info("  409(경합 실패) 건수   : {}", conflictCount.get());
        log.info("  예상치 못한 예외 건수 : {}", unexpectedErrorCount.get());
        log.info("  최종 booking_status   : {}", reloaded.getBookingStatus());
        log.info("============================================================");

        assertThat(unexpectedErrorCount.get()).as("의도치 않은 예외는 없어야 한다").isZero();
        assertThat(successCount.get() + conflictCount.get()).as("모든 스레드가 경합에 참여해야 한다").isEqualTo(THREAD_COUNT);
        assertThat(successCount.get()).as("정확히 1건만 성공해야 한다").isEqualTo(1);
        assertThat(conflictCount.get()).as("나머지는 전부 409(경합 실패)여야 한다").isEqualTo(THREAD_COUNT - 1);
        assertThat(reloaded.getBookingStatus()).isEqualTo(BookingStatus.OPEN);
    }
}
