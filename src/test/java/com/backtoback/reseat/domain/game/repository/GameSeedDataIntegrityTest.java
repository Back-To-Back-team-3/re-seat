package com.backtoback.reseat.domain.game.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;

/**
 * KBO 잔여 경기 시드 마이그레이션 데이터 무결성 검증.
 */
@EnabledIfEnvironmentVariable(
    named = "RUN_SEED_INTEGRITY_TESTS",
    matches = "true"
)
@Tag("seed-integrity")
@ActiveProfiles("local")
@SpringBootTest
class GameSeedDataIntegrityTest {

    // V31이 삽입한 범위(2026-09-08 ~ 2026-10-07, 107건)의 경계값
    private static final LocalDateTime V31_RANGE_START = LocalDateTime.of(2026, 9, 8, 0, 0);
    private static final LocalDateTime V31_RANGE_END = LocalDateTime.of(2026, 10, 7, 23, 59, 59);
    private static final int V31_EXPECTED_COUNT = 107;

    @Autowired
    private GameRepository gameRepository;

    @Test
    @DisplayName("모든 경기는 홈팀과 원정팀이 다르다 (전역 불변조건, 전체 377건 대상)")
    void should_haveDifferentHomeAndAwayTeam_forAllGames() {
        List<Game> games = gameRepository.findAll();

        assertThat(games).isNotEmpty();
        assertThat(games)
            .allSatisfy(game -> assertThat(game.getHomeTeam().getId()).isNotEqualTo(game.getAwayTeam().getId()));
    }

    @Test
    @DisplayName("V31 시드가 정확히 107건 삽입되고, 전건이 SCHEDULED 및 예매 규칙을 만족한다")
    void should_insertExactly107GamesWithCorrectBookingRules_forV31Range() {
        List<Game> v31Games = gameRepository.findByGameAtBetween(V31_RANGE_START, V31_RANGE_END);

        // 부분 삽입(일부만 들어가고 실패)을 잡기 위한 정확한 건수 검증
        assertThat(v31Games).as("V31 범위(09.08~10.07) 경기는 정확히 107건이어야 한다").hasSize(V31_EXPECTED_COUNT);

        assertThat(v31Games).allSatisfy(game -> {
            assertThat(game.getBookingStatus()).as("V31 경기는 전건 SCHEDULED로 시작해야 한다").isEqualTo(BookingStatus.SCHEDULED);
            assertThat(game.getBookingOpenAt())
                .as("booking_open_at = game_at - 7일 규칙 위반: gameId=" + game.getId())
                .isEqualTo(game.getGameAt().minusDays(7));
            assertThat(game.getBookingCloseAt())
                .as("booking_close_at = game_at 규칙 위반: gameId=" + game.getId())
                .isEqualTo(game.getGameAt());
        });
    }
}
