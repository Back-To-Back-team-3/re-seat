package com.backtoback.reseat.domain.game.repository;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.service.GameSearchCondition;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
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
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
                if (game.getHomeTeam() != null) {
                    game.getHomeTeam().getName();
                }
                if (game.getAwayTeam() != null) {
                    game.getAwayTeam().getName();
                }
                if (game.getStadium() != null) {
                    game.getStadium().getName();
                }
            });

            // content 1번 쿼리 + count 1번 쿼리 도합 2번으로 제어되는지 확인
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("현재 상태가 기대값과 같으면 조건부 UPDATE가 1건을 반환하고 상태가 전이된다")
    void should_returnOne_when_currentStatusMatches() {
        Game saved = gameRepository.save(gameFixture(BookingStatus.SCHEDULED));
        entityManager.flush();
        int updated
            = gameRepository.compareAndSetBookingStatus(saved.getId(), BookingStatus.SCHEDULED, BookingStatus.OPEN);
        assertThat(updated).isEqualTo(1);
        entityManager.clear(); // 벌크 UPDATE는 1차 캐시를 갱신하지 않으므로 재조회로 확인
        assertThat(gameRepository.findById(saved.getId()).orElseThrow().getBookingStatus())
            .isEqualTo(BookingStatus.OPEN);
    }

    @Test
    @DisplayName("이미 다른 상태로 바뀐 경기는 조건부 UPDATE가 0건을 반환하고 상태가 유지된다")
    void should_returnZero_when_currentStatusAlreadyChanged() {
        Game saved = gameRepository.save(gameFixture(BookingStatus.OPEN));
        entityManager.flush();
        // 다른 트랜잭션이 먼저 OPEN으로 바꾼 뒤 도착한 요청을 재현: expectedCurrent를 SCHEDULED로 잘못 전달
        int updated
            = gameRepository.compareAndSetBookingStatus(saved.getId(), BookingStatus.SCHEDULED, BookingStatus.OPEN);
        assertThat(updated).isZero();
        entityManager.clear();
        assertThat(gameRepository.findById(saved.getId()).orElseThrow().getBookingStatus())
            .isEqualTo(BookingStatus.OPEN); // 변경 없이 유지
    }

    /** Team.of/Stadium.of 정적 팩터리를 그대로 사용해 다른 테스트와 컨벤션을 맞춘다. */
    private Game gameFixture(BookingStatus bookingStatus) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Stadium stadium = Stadium.of("테스트 구장-" + suffix, "서울시 테스트구", 10_000);
        entityManager.persist(stadium);

        Team homeTeam = Team.of("테스트 홈팀-" + suffix, stadium);
        Team awayTeam = Team.of("테스트 원정팀-" + suffix, stadium);
        entityManager.persist(homeTeam);
        entityManager.persist(awayTeam);

        LocalDateTime now = LocalDateTime.now();

        return Game
            .builder()
            .homeTeam(homeTeam)
            .awayTeam(awayTeam)
            .stadium(stadium)
            .gameAt(now.plusDays(7))
            .bookingOpenAt(now)
            .bookingCloseAt(now.plusDays(7))
            .bookingStatus(bookingStatus)
            .title("테스트 경기")
            .build();
    }

    /**
     * Repository 테스트용 QueryDSL 설정.
     * <p>@DataJpaTest는 전체 애플리케이션 설정을 띄우지 않으므로
     * JPAQueryFactory Bean을 테스트에서 직접 등록한다.</p>
     */
    @TestConfiguration
    @EnableJpaAuditing
    static class QuerydslTestConfig {

        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }
    }
}
