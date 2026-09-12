package com.backtoback.reseat.domain.seatinventory.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.seatinventory.dto.SeatInventorySummaryResponse;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.exception.SeatInventoryNotOpenedException;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.stadium.repository.SeatRepository;
import com.backtoback.reseat.domain.stadium.repository.SeatZoneRepository;
import com.backtoback.reseat.domain.stadium.repository.StadiumRepository;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.team.repository.TeamRepository;

import jakarta.persistence.EntityManager;

/**
 * SeatQueryService.summarize() 통합 테스트.
 * <p>N+1 회귀 검증(SeatQueryServiceTest)과 책임이 달라 별도 파일로 분리한다.
 * summarize()의 쿼리 횟수(countByGameIdAndStatus 4회)는 이미 설계로 확정된 동작이라 여기서는 결과값 정합성만 검증한다.
 * <p>test 프로필(H2)에는 시드 데이터가 없으므로 구장·구역·좌석·구단·경기를 직접 준비한다.
 */
@SpringBootTest
@Transactional
class SeatQuerySummaryServiceTest {

    @Autowired
    private SeatQueryService seatQueryService;

    @Autowired
    private GameSeatCreateService gameSeatCreateService;

    @Autowired
    private StadiumRepository stadiumRepository;

    @Autowired
    private SeatZoneRepository seatZoneRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private EntityManager entityManager;

    private Long gameId;

    @BeforeEach
    void setUp() {
        Stadium stadium = stadiumRepository.save(Stadium.of("테스트 구장", "서울시 테스트구", 10_000));
        Team homeTeam = teamRepository.save(Team.of("홈팀", stadium));
        Team awayTeam = teamRepository.save(Team.of("원정팀", stadium));

        SeatZone zone = seatZoneRepository.save(SeatZone.of(stadium, "테스트 구역", SeatGrade.INFIELD, 18_000));
        for (int i = 1; i <= 4; i++) {
            seatRepository.save(Seat.of(stadium, zone, "A", "1", String.valueOf(i)));
        }

        LocalDateTime now = LocalDateTime.now();
        Game game
            = Game
                .builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium(stadium)
                .gameAt(now.plusDays(7))
                .bookingOpenAt(now)
                .bookingCloseAt(now.plusDays(7))
                .bookingStatus(BookingStatus.SCHEDULED)
                .title("테스트 경기")
                .build();

        gameId = gameRepository.save(game).getId();
    }

    @DisplayName("좌석 4석 중 상태를 각각 다르게 바꾸면 요약 조회 결과에 그대로 반영된다")
    @Test
    void should_returnActualCounts_when_seatsHaveMixedStatus() {
        gameSeatCreateService.openInventory(gameId);
        entityManager.flush();
        entityManager.clear();

        // 4석 중 1석은 HELD, 1석은 BLOCKED로 바꾸고 나머지 2석은 AVAILABLE로 둔다
        List<GameSeat> gameSeats
            = entityManager
                .createQuery("select gs from GameSeat gs where gs.game.id = :gameId order by gs.id asc", GameSeat.class)
                .setParameter("gameId", gameId)
                .getResultList();

        gameSeats.get(0).hold(LocalDateTime.now().plusMinutes(10));
        gameSeats.get(1).block();
        entityManager.flush();
        entityManager.clear();

        SeatInventorySummaryResponse response = seatQueryService.summarize(gameId);

        assertThat(response.available()).isEqualTo(2L);
        assertThat(response.held()).isEqualTo(1L);
        assertThat(response.sold()).isEqualTo(0L);
        assertThat(response.blocked()).isEqualTo(1L);
    }

    @DisplayName("존재하지 않는 경기를 요약 조회하면 GameNotFoundException이 발생한다")
    @Test
    void should_throwGameNotFound_when_gameIdNotExists() {
        assertThatThrownBy(() -> seatQueryService.summarize(999_999L)).isInstanceOf(GameNotFoundException.class);
    }

    @DisplayName("재고가 오픈되지 않은 경기를 요약 조회하면 SeatInventoryNotOpenedException이 발생한다")
    @Test
    void should_throwSeatInventoryNotOpened_when_inventoryNotOpened() {
        assertThatThrownBy(() -> seatQueryService.summarize(gameId))
            .isInstanceOf(SeatInventoryNotOpenedException.class);
    }
}
