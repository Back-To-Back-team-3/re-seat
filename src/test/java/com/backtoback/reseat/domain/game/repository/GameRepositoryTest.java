package com.backtoback.reseat.domain.game.repository;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.service.GameSearchCondition;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;

/**
 * 경기 Repository 조회 테스트.
 * <p>목록 조회 시 homeTeam, awayTeam, stadium 정보를 함께 조회할 수 있는지 확인한다.</p>
 */
@DataJpaTest(
    properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true"
    }
)
@Import(
    {
        GameRepositoryImpl.class,
        GameRepositoryTest.QuerydslTestConfig.class
    }
)
class GameRepositoryTest {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("경기 목록 조회 시 팀과 구장 정보를 함께 조회한다")
    void should_fetchTeamsAndStadium_when_searchGames() {
        GameSearchCondition condition = new GameSearchCondition(null, null, null, null, null);

        Page<Game> result = gameRepository.searchGames(condition, PageRequest.of(0, 20));

        List<Game> games = result.getContent();

        assertThat(games).isNotNull();

        if (!games.isEmpty()) {
            Game game = games.get(0);

            assertThat(game.getHomeTeam()).isNotNull();
            assertThat(game.getAwayTeam()).isNotNull();
            assertThat(game.getStadium()).isNotNull();
        }
    }

    @Test
    @DisplayName("경기 목록 조회 시 팀·구장을 fetch join하여 추가 쿼리가 발생하지 않는다")
    void should_notCauseNPlusOne_when_fetchJoinApplied() {

        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Page<Game> result
            = gameRepository.searchGames(new GameSearchCondition(null, null, null, null, null), PageRequest.of(0, 20));

        List<Game> content = result.getContent();

        if (!content.isEmpty()) {
            content.forEach(game -> {
                if (game.getHomeTeam() != null)
                    game.getHomeTeam().getName();
                if (game.getAwayTeam() != null)
                    game.getAwayTeam().getName();
                if (game.getStadium() != null)
                    game.getStadium().getName();
            });

            // content 1번 쿼리 + count 1번 쿼리 도합 2번으로 제어되는지 확인
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        }
    }

    /**
     * Repository 테스트용 QueryDSL 설정.
     * <p>@DataJpaTest는 전체 애플리케이션 설정을 띄우지 않으므로
     * JPAQueryFactory Bean을 테스트에서 직접 등록한다.</p>
     */
    @TestConfiguration
    static class QuerydslTestConfig {

        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }
    }
}
