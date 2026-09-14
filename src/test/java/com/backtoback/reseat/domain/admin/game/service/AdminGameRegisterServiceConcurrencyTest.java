package com.backtoback.reseat.domain.admin.game.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.backtoback.reseat.domain.admin.game.dto.request.GameRegisterRequest;
import com.backtoback.reseat.domain.game.exception.DuplicateGameException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.stadium.repository.StadiumRepository;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.team.repository.TeamRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * [이슈 #476] AdminGameRegisterService.registerGame() 완전 동시 요청 경합 테스트.
 * <p>existsBy 사전 검증은 순차 요청만 방어하므로, 두 스레드가 검증을 동시에 통과한 뒤
 * save() 시점에 UNIQUE 제약(uk_games_stadium_game_at)이 실제로 DuplicateGameException으로
 * 매핑되는지 증명한다.
 */
@EnabledIfEnvironmentVariable(
    named = "RUN_CONCURRENCY_TESTS",
    matches = "true"
)
@Tag("concurrency")
@ActiveProfiles("test-concurrency")
@SpringBootTest
@Slf4j
class AdminGameRegisterServiceConcurrencyTest {

    private static final int THREAD_COUNT = 2;

    @Autowired
    private AdminGameRegisterService adminGameRegisterService;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private StadiumRepository stadiumRepository;
    @Autowired
    private TeamRepository teamRepository;

    private Long stadiumId;
    private Long homeTeamId;
    private Long awayTeamId;
    private GameRegisterRequest request;

    @BeforeEach
    void setUp() {
        Stadium stadium = stadiumRepository.save(Stadium.of("동시성 등록 테스트 구장", "서울시 테스트구 3", 10_000));
        stadiumId = stadium.getId();

        Team homeTeam = teamRepository.save(Team.of("홈팀", stadium));
        Team awayTeam = teamRepository.save(Team.of("원정팀", stadium));
        homeTeamId = homeTeam.getId();
        awayTeamId = awayTeam.getId();

        LocalDateTime now = LocalDateTime.now();
        request
            = new GameRegisterRequest(
                stadiumId,
                homeTeamId,
                awayTeamId,
                now.plusDays(7),
                now,
                now.plusDays(6),
                "동시성 등록 테스트 경기"
            );
    }

    @AfterEach
    void tearDown() {
        gameRepository
            .deleteAll(
                gameRepository.findByGameAtBetween(request.gameAt().minusMinutes(1), request.gameAt().plusMinutes(1))
            );
        teamRepository.deleteById(homeTeamId);
        teamRepository.deleteById(awayTeamId);
        stadiumRepository.deleteById(stadiumId);
    }

    @Test
    @DisplayName("관리자 2명이 동일 구장·동일 일시로 registerGame()을 동시 호출하면 1건만 성공하고 나머지는 DUPLICATE_GAME이다")
    void should_succeedOnce_when_twoAdminsRegisterSameGameConcurrently() throws InterruptedException {
        // given
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);
        AtomicLong createdGameId = new AtomicLong(-1);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // when
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    var response = adminGameRegisterService.registerGame(request);
                    createdGameId.set(response.gameId());
                    successCount.incrementAndGet();
                } catch (DuplicateGameException e) {
                    duplicateCount.incrementAndGet();
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

        log.info("============================================================");
        log.info("[이슈 #476] AdminGameRegisterService 동시 등록 테스트 수치");
        log.info("  동시 스레드 수          : {}", THREAD_COUNT);
        log.info("  성공 건수               : {}", successCount.get());
        log.info("  DUPLICATE_GAME 건수     : {}", duplicateCount.get());
        log.info("  예상치 못한 예외 건수   : {}", unexpectedErrorCount.get());
        log
            .info(
                "  실제 저장된 게임 행 수  : {}",
                gameRepository
                    .findByGameAtBetween(request.gameAt().minusMinutes(1), request.gameAt().plusMinutes(1))
                    .size()
            );
        log.info("============================================================");

        assertThat(unexpectedErrorCount.get()).as("의도치 않은 예외는 없어야 한다").isZero();
        assertThat(successCount.get() + duplicateCount.get()).as("모든 스레드가 경합에 참여해야 한다").isEqualTo(THREAD_COUNT);
        assertThat(successCount.get()).as("정확히 1건만 성공해야 한다").isEqualTo(1);
        assertThat(duplicateCount.get()).as("나머지는 전부 DUPLICATE_GAME이어야 한다").isEqualTo(THREAD_COUNT - 1);
        assertThat(createdGameId.get()).as("성공한 스레드의 gameId가 기록돼야 한다").isNotEqualTo(-1L);
    }
}
