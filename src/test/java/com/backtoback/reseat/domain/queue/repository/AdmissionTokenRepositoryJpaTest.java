package com.backtoback.reseat.domain.queue.repository;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.global.config.JpaConfig;
import com.backtoback.reseat.global.config.QuerydslConfig;

import jakarta.persistence.EntityManager;

/**
 * 관리자 대기열 현황과 입장 지표에 사용하는 AdmissionToken 집계 쿼리를 검증한다.
 */
@DataJpaTest
@Import(
    {
        QuerydslConfig.class,
        JpaConfig.class
    }
)
@DisplayName("AdmissionTokenRepository 관리자 집계")
public class AdmissionTokenRepositoryJpaTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 12, 0);
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 9, 1, 0, 0);
    private static final LocalDateTime TO_EXCLUSIVE = LocalDateTime.of(2026, 10, 1, 0, 0);

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private AdmissionTokenRepository admissionTokenRepository;

    private User user;
    private Game game;
    private Game otherGame;

    /**
     * 집계 쿼리 테스트에 공통으로 사용할 경기 두 개와 사용자를 저장한다.
     */
    @BeforeEach
    void setUp() {

        Stadium stadium = Stadium.of("Queue 집계 테스트 구장", "테스트시 테스트구", 10_000);
        entityManager.persist(stadium);

        Team homeTeam = Team.of("Queue 집계 홈팀", stadium);
        Team awayTeam = Team.of("Queue 집계 원정팀", stadium);
        entityManager.persist(homeTeam);
        entityManager.persist(awayTeam);

        game = createGame("Queue 집계 대상 경기", stadium, homeTeam, awayTeam);
        otherGame = createGame("Queue 집계 제외 경기", stadium, homeTeam, awayTeam, NOW.plusDays(8));

        user
            = User
                .builder()
                .email("queue-metric@test.com")
                .password("test")
                .name("Queue 지표 사용자")
                .phone("010-0000-0001")
                .isVerified(true)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        entityManager.persist(user);
    }

    /**
     * 영속성 Context의 변경 사항을 DB에 반영하고 초기화한다.
     */
    private void flushAndClear() {

        entityManager.flush();
        entityManager.clear();
    }

    /**
     * 집계 테스트에 사용할 경기를 저장한다.
     *
     * @param title 경기 제목
     * @param stadium 경기장
     * @param homeTeam 홈팀
     * @param awayTeam 원정팀
     * @return 저장된 경기
     */
    private Game createGame(String title, Stadium stadium, Team homeTeam, Team awayTeam) {

        return createGame(title, stadium, homeTeam, awayTeam, NOW.plusDays(7));
    }

    /**
     * 집계 테스트에 사용할 경기를 저장한다.
     * <p>gameAt을 직접 지정해, 동일 구장·동일 일시 중복 등록 제약(uk_games_stadium_game_at)에
     * 걸리지 않도록 경기별로 다른 시각을 부여할 수 있다.</p>
     *
     * @param title 경기 제목
     * @param stadium 경기장
     * @param homeTeam 홈팀
     * @param awayTeam 원정팀
     * @param gameAt 경기 일시
     * @return 저장된 경기
     */
    private Game createGame(String title, Stadium stadium, Team homeTeam, Team awayTeam, LocalDateTime gameAt) {

        Game savedGame
            = Game
                .builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium(stadium)
                .gameAt(gameAt)
                .bookingOpenAt(gameAt.minusDays(8))
                .bookingCloseAt(gameAt.minusDays(1))
                .bookingStatus(BookingStatus.OPEN)
                .title(title)
                .build();
        entityManager.persist(savedGame);

        return savedGame;
    }

    /**
     * 전달받은 시간 조건으로 ACTIVE Queue-Token을 저장한다.
     *
     * @param game 토큰을 발급할 경기
     * @param user 토큰을 발급받을 사용자
     * @param token 토큰 문자열
     * @param issuedAt 발급 시간
     * @param expiresAt 전체 만료 시간
     * @param seatBrowsingExpiresAt 최초 좌석 탐색 만료 시간
     * @return 저장된 Queue-Token
     */
    private AdmissionToken saveToken(
        Game game,
        User user,
        String token,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        LocalDateTime seatBrowsingExpiresAt
    ) {

        return admissionTokenRepository
            .save(AdmissionToken.of(game, user, token, issuedAt, expiresAt, seatBrowsingExpiresAt));
    }

    // ---------- 사용 가능한 Queue-Token 집계 ----------

    @Test
    @DisplayName("전체 만료 시간과 최초 탐색 시간이 남은 ACTIVE Queue-Token을 사용할 수 있는 토큰으로 집계한다.")
    void countUsableByGameId_withUsableActiveToken_countsToken() {

        // given
        // 전체 만료시간과 최초 탐색 만료시간이 모두 남은 ACTIVE 토큰 — 포함
        saveToken(game, user, "usable-token", NOW.minusMinutes(1), NOW.plusMinutes(20), NOW.plusMinutes(2));
        flushAndClear();

        // when
        long usableCount = admissionTokenRepository.countUsableByGameId(game.getId(), AdmissionTokenStatus.ACTIVE, NOW);

        // then
        // 관리자 현황도 실제 Queue-Token 검증과 같은 전체 · 최초 탐색 만료 조건을 사용해야 한다.
        assertThat(usableCount).isEqualTo(1);
    }

    @Test
    @DisplayName("전체 만료 시간이 기준 시간과 같으면 사용할 수 있는 Queue-Token에서 제외한다.")
    void countUsableByGameId_withExpiresAtEqualToNow_excludesToken() {

        // given
        // 전체 만료시간이 조회 기준 시간과 같은 ACTIVE 토큰 — 제외
        saveToken(game, user, "expired-at-boundary-token", NOW.minusMinutes(1), NOW, NOW.plusMinutes(2));
        flushAndClear();

        // when
        long usableCount = admissionTokenRepository.countUsableByGameId(game.getId(), AdmissionTokenStatus.ACTIVE, NOW);

        // then
        assertThat(usableCount).isEqualTo(0);
    }

    @Test
    @DisplayName("최초 탐색 미완료 토큰은 탐색 만료 시간이 기준 시간과 같으면 제외한다.")
    void countUsableByGameId_withBrowsingExpiresAtEqualToNow_excludesToken() {

        // given
        // 탐색 미완료이고 최초 탐색 만료시간이 조회 기준 시간과 같은 ACTIVE 토큰 — 제외
        saveToken(game, user, "browsing-expired-at-boundary-token", NOW.minusMinutes(1), NOW.plusMinutes(20), NOW);
        flushAndClear();

        // when
        long usableCount = admissionTokenRepository.countUsableByGameId(game.getId(), AdmissionTokenStatus.ACTIVE, NOW);

        // then
        assertThat(usableCount).isEqualTo(0);
    }

    @Test
    @DisplayName("좌석 탐색을 완료한 토큰은 최초 탐색 기한이 지나도 전체 만료 전이면 포함한다.")
    void countUsableByGameId_withCompletedBrowsing_countsToken() {

        // given
        // 탐색을 완료했고 전체 만료시간이 남은 ACTIVE 토큰 — 포함
        AdmissionToken completedBrowsingToken
            = saveToken(
                game,
                user,
                "completed-browsing-token",
                NOW.minusMinutes(5),
                NOW.plusMinutes(20),
                NOW.minusMinutes(1)
            );
        completedBrowsingToken.completeSeatBrowsing(NOW.minusMinutes(2));
        flushAndClear();

        // when
        long usableCount = admissionTokenRepository.countUsableByGameId(game.getId(), AdmissionTokenStatus.ACTIVE, NOW);

        // then
        assertThat(usableCount).isEqualTo(1);
    }

    // ---------- Queue-Token 발급 지표 집계 ----------

    @Test
    @DisplayName("발급 수 조회는 시작 시간을 포함하고 종료 시간을 제외하며 다른 경기 토큰을 제외한다.")
    void countIssuedByGameIdAndPeriod_appliesTimeAndGameBoundaries() {

        // given
        // 시작 경계에 있는 대상 경기 토큰 — 포함
        saveToken(game, user, "from-token", FROM, NOW.plusMinutes(20), NOW.plusMinutes(3));

        // 종료 직전에 있는 대상 경기 토큰 — 포함
        saveToken(
            game,
            user,
            "before-end-token",
            TO_EXCLUSIVE.minusNanos(1_000L),
            TO_EXCLUSIVE.plusMinutes(20),
            TO_EXCLUSIVE.plusMinutes(3)
        );

        // 종료 경계와 같은 대상 경기 토큰 — 제외
        saveToken(game, user, "at-end-token", TO_EXCLUSIVE, TO_EXCLUSIVE.plusMinutes(20), TO_EXCLUSIVE.plusMinutes(3));

        // 조회 범위 안에 있지만 다른 경기의 토큰 — 제외
        saveToken(otherGame, user, "other-game-token", FROM.plusDays(1), NOW.plusMinutes(20), NOW.plusMinutes(3));
        flushAndClear();

        // when
        long issuedCount = admissionTokenRepository.countIssuedByGameIdAndPeriod(game.getId(), FROM, TO_EXCLUSIVE);

        // then
        // 종료 날짜의 다음 날 00:00 미만으로 조회해 to 날짜 전체를 포함한다.
        assertThat(issuedCount).isEqualTo(2);
    }

    @Test
    @DisplayName("Queue-Token 발급 수를 날짜별로 집계한다.")
    void findDailyAdmissionMetrics_groupsTokensByIssuedDate() {

        // given
        // 조회 시작일에 발급된 첫 번째 대상 경기 토큰 — 첫 번째 날짜에 포함
        saveToken(game, user, "first-day-token-1", FROM, NOW.plusMinutes(20), NOW.plusMinutes(3));

        // 조회 시작일에 발급된 두 번째 대상 경기 토큰 — 첫 번째 날짜에 포함
        saveToken(game, user, "first-day-token-2", FROM, NOW.plusMinutes(20), NOW.plusMinutes(3));

        // 조회 시작일 다음 날에 발급된 대상 경기 토큰 — 두 번째 날짜에 포함
        saveToken(game, user, "second-day-token", FROM.plusDays(1), NOW.plusMinutes(20), NOW.plusMinutes(3));
        flushAndClear();

        // when
        List<AdmissionMetricDailyProjection> dailyMetrics
            = admissionTokenRepository.findDailyAdmissionMetrics(game.getId(), FROM, TO_EXCLUSIVE);

        // then
        assertThat(dailyMetrics).hasSize(2);

        // 첫 번째 날짜(FROM) 발급 수 검증
        assertThat(dailyMetrics.get(0).getAdmissionDate()).isEqualTo(FROM.toLocalDate());
        assertThat(dailyMetrics.get(0).getAdmittedCount()).isEqualTo(2L);

        // 두 번째 날짜(FROM.plusDays(1)) 발급 수 검증
        assertThat(dailyMetrics.get(1).getAdmissionDate()).isEqualTo(FROM.plusDays(1).toLocalDate());
        assertThat(dailyMetrics.get(1).getAdmittedCount()).isEqualTo(1L);
    }
}
