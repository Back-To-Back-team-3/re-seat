package com.backtoback.reseat.domain.game.service;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.stadium.repository.StadiumRepository;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 경기 예매 오픈 동시성 테스트.
 * <p>여러 관리자가 동시에 같은 경기의 예매를 열려고 해도 딱 한 명만 성공해야 한다는 걸 확인한다.
 * <p>@Transactional을 쓰지 않고 @AfterEach로 직접 데이터를 지운다.
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
@Import(GameBookingOpenConcurrencyTest.TestTransactionHelper.class)
class GameBookingOpenConcurrencyTest {

    private static final int THREAD_COUNT = 10;

    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private StadiumRepository stadiumRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TestTransactionHelper transactionHelper;

    // 테스트 픽스처 ID — @AfterEach 수동 정리에 사용
    private Long gameId;
    private Long stadiumId;
    private Long homeTeamId;
    private Long awayTeamId;

    // @Transactional 금지 → @AfterEach 수동 정리
    @BeforeEach
    void setUp() {
        Stadium stadium = Stadium.of("테스트 구장", "서울시 테스트구 1", 10_000);
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
                .bookingStatus(BookingStatus.SCHEDULED)
                .title("[C-9-6] 예매 오픈 동시성 테스트 경기")
                .build();
        gameRepository.save(game);
        gameId = game.getId();
    }

    /**
     * FK 역참조 역순 삭제 순서: games(team, stadium 참조) → teams(stadium 참조) → stadiums
     */
    @AfterEach
    void tearDown() {
        if (gameId != null) {
            gameRepository.deleteById(gameId);
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
    }

    @Test
    @DisplayName("동일 경기에 예매 오픈을 동시 10건 요청하면 정확히 1건만 성공한다")
    void should_succeedExactlyOnce_when_concurrentOpenRequests() throws InterruptedException {
        // given
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    int updated
                        = gameRepository
                            .compareAndSetBookingStatus(gameId, BookingStatus.SCHEDULED, BookingStatus.OPEN);
                    if (updated == 1) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("스레드 실행 중 예외 발생", e);
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

        Game reloaded = gameRepository.findById(gameId).orElseThrow();

        log.info("=====================================================");
        log.info("[C-9-6] 예매 오픈 동시성 테스트 수치");
        log.info("  동시 스레드 수      : {}", THREAD_COUNT);
        log.info("  성공(1 반환) 건수   : {}", successCount.get());
        log.info("  실패(0 반환) 건수   : {}", failCount.get());
        log.info("  최종 booking_status : {}", reloaded.getBookingStatus());
        log.info("=====================================================");

        assertThat(successCount.get() + failCount.get()).as("모든 스레드가 경합에 참여해야 한다.").isEqualTo(THREAD_COUNT);
        assertThat(successCount.get()).as("정확히 1건만 성공해야 한다.").isEqualTo(1);
        assertThat(failCount.get()).as("나머지는 전부 0 반환(경합 실패)이어야 한다.").isEqualTo(THREAD_COUNT - 1);
        assertThat(reloaded.getBookingStatus()).isEqualTo(BookingStatus.OPEN);
    }

    /**
     * 테스트 스레드에서 @Modifying 쿼리를 실행하기 위한 트랜잭션 경계.
     */
    @TestConfiguration
    @RequiredArgsConstructor
    static class TestTransactionHelper {

        private final GameRepository gameRepository;

        @Transactional
        public int compareAndSetBookingStatus(Long gameId, BookingStatus expectedCurrent, BookingStatus target) {
            return gameRepository.compareAndSetBookingStatus(gameId, expectedCurrent, target);
        }
    }
}
