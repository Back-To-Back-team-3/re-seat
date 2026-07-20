package com.backtoback.reseat.domain.seatinventory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.backtoback.reseat.domain.seatinventory.dto.SeatStatusResponse;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.Disabled;

/**
 * SeatQueryService의 N+1 회귀 테스트.
 */
@Disabled("테스트 제외")
@SpringBootTest
@Transactional
class SeatQueryServiceTest {

    private static final long SEEDED_STADIUM_ID = 1L;

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
        gameIdWithSeats = findFirstGameIdOfStadium();
        gameSeatCreateService.openInventory(gameIdWithSeats);

        SessionFactory sessionFactory = entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class);
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
        List<SeatStatusResponse> seats =
            seatQueryService.getSeats(gameIdWithSeats, null, null, null);

        // then
        assertThat(seats).hasSize(500);
        // 3단계 fetch join(game_seats → seats → seat_zones)이 정상 동작하면
        // validateGame()의 existsById() 쿼리 1건 + fetch join 조회 쿼리 1건 = 2건.
        // 실행 쿼리는 2건이어야 한다. N+1이 발생하면 500건 이상으로 튄다.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2L);
    }

    private Long findFirstGameIdOfStadium() {
        return entityManager.createQuery(
                "SELECT g.id FROM Game g WHERE g.stadium.id = :stadiumId ORDER BY g.id ASC",
                Long.class)
            .setParameter("stadiumId", SEEDED_STADIUM_ID)
            .setMaxResults(1)
            .getSingleResult();
    }
}
