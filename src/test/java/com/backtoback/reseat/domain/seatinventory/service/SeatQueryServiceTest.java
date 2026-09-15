package com.backtoback.reseat.domain.seatinventory.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.seatinventory.dto.SeatStatusResponse;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;

import jakarta.persistence.EntityManager;

/**
 * SeatQueryService.getSeats()의 N+1 회귀 테스트.
 * <p>3단계 fetch join(game_seats → seats → seat_zones)이 정상 동작하면
 * 실행 쿼리는 existsById() 1건 + fetch join 조회 1건, 총 2건이어야 한다.
 * <p>구장·구역·좌석·경기 픽스처는 CSV 시드에 기대지 않고 이 클래스가 직접 만든다.
 */
@SpringBootTest
@Transactional
class SeatQueryServiceTest {

    /**
     * N+1 재현 목적의 픽스처이므로 건수 자체는 의미가 없다 — 여러 건이기만 하면 fetch join 여부가 드러난다.
     */
    private static final int SEAT_COUNT = 4;

    @Autowired
    private SeatQueryService seatQueryService;

    @Autowired
    private GameSeatCreateService gameSeatCreateService;

    @Autowired
    private EntityManager entityManager;

    private Long gameIdWithSeats;
    private Statistics statistics;

    @SuppressWarnings("resource") // Spring 관리 SessionFactory이므로 여기서 닫지 않는다
    @BeforeEach
    void setUp() {
        // 시드(stadium_id=1)에 의존하지 않고 이 테스트에 필요한 최소 픽스처를 직접 만든다.
        Stadium stadium = persistStadium("테스트구장");
        Team homeTeam = persistTeam("홈팀", stadium);
        Team awayTeam = persistTeam("원정팀", stadium);
        SeatZone zone = persistSeatZone(stadium, "테스트구역", SeatGrade.INFIELD, 15000);
        persistSeats(stadium, zone, SEAT_COUNT);

        Game game = persistGame(homeTeam, awayTeam, stadium);
        gameIdWithSeats = game.getId();
        entityManager.flush();

        gameSeatCreateService.openInventory(gameIdWithSeats);

        // 통계 집계는 픽스처 생성이 끝난 뒤부터 시작해야 한다.
        // 여기서 clear()하지 않으면 위 INSERT 쿼리들까지 prepareStatementCount에 섞여 assertion이 왜곡된다.
        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);

        entityManager.flush();
        entityManager.clear();
        statistics.clear();
    }

    @DisplayName("fetch join 적용 시 N+1 없이 단일 쿼리로 500건이 조회된다")
    @Test
    void should_notCauseNPlusOne_when_fetchJoinApplied() {
        // when
        List<SeatStatusResponse> seats = seatQueryService.getSeats(gameIdWithSeats, null, null, null);

        // then
        assertThat(seats).hasSize(SEAT_COUNT);
        // 3단계 fetch join(game_seats → seats → seat_zones)이 정상 동작하면
        // validateGame()의 existsById() 쿼리 1건 + fetch join 조회 쿼리 1건 = 2건.
        // 좌석 건수와 무관하게 실행 쿼리는 항상 2건이어야 한다.
        // N+1이 발생하면 좌석 건수만큼 튄다.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2L);
    }

    // ---------- 픽스처 생성 헬퍼 ----------

    private Stadium persistStadium(String name) {
        Stadium stadium = Stadium.of(name, "테스트주소", 30000);
        entityManager.persist(stadium);
        return stadium;
    }

    private Team persistTeam(String name, Stadium homeStadium) {
        Team team = Team.of(name, homeStadium);
        entityManager.persist(team);
        return team;
    }

    private SeatZone persistSeatZone(Stadium stadium, String name, SeatGrade grade, int basePrice) {
        SeatZone zone = SeatZone.of(stadium, name, grade, basePrice);
        entityManager.persist(zone);
        return zone;
    }

    private void persistSeats(Stadium stadium, SeatZone zone, int count) {
        for (int i = 0; i < count; i++) {
            Seat seat = Seat.of(stadium, zone, "A", "1", String.valueOf(i + 1));
            entityManager.persist(seat);
        }
    }

    private Game persistGame(Team homeTeam, Team awayTeam, Stadium stadium) {
        LocalDateTime gameAt = LocalDateTime.now().plusDays(7);
        Game game
            = Game
                .builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium(stadium)
                .gameAt(gameAt)
                .bookingOpenAt(gameAt.minusDays(6))
                .bookingCloseAt(gameAt.minusHours(1))
                .bookingStatus(BookingStatus.SCHEDULED)
                .build();
        entityManager.persist(game);
        return game;
    }
}
