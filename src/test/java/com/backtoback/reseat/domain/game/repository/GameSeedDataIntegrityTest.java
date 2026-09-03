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

    @Autowired
    private GameRepository gameRepository;

    @Test
    @DisplayName("모든 경기는 홈팀과 원정팀이 다르다")
    void should_haveDifferentHomeAndAwayTeam_forAllGames() {
        List<Game> games = gameRepository.findAll();

        assertThat(games).isNotEmpty();
        assertThat(games)
            .allSatisfy(game -> assertThat(game.getHomeTeam().getId()).isNotEqualTo(game.getAwayTeam().getId()));
    }

    @Test
    @DisplayName("발표일(09.16) 이후 예매 가능한 SCHEDULED 경기가 1건 이상 존재한다")
    void should_haveAtLeastOneScheduledGame_afterAnnouncementDate() {
        long count = gameRepository.countByGameAtAfter(LocalDateTime.of(2026, 9, 16, 0, 0));

        assertThat(count).isGreaterThan(0);
    }
}
