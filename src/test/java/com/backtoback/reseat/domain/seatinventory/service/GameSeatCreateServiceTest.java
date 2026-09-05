package com.backtoback.reseat.domain.seatinventory.service;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.seatinventory.dto.GameSeatOpenResponse;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.exception.SeatInventoryAlreadyOpenedException;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.global.common.BaseIntegrationTest;
import com.backtoback.reseat.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 좌석 재고 생성 서비스 통합 테스트.
 * <p>
 * 시드 데이터(teams.csv 등)에 의존하지 않고, 각 테스트가 필요한 구장·좌석·경기를
 *
 * @BeforeEach에서 직접 만든다. Testcontainers(BaseIntegrationTest)로 매번 빈 DB에서
 * 시작하므로 "이미 시드가 있다"는 가정을 두지 않는다.
 */
@Import(QuerydslConfig.class)
@Transactional
class GameSeatCreateServiceTest extends BaseIntegrationTest {

    private static final int EXPECTED_SEAT_COUNT = 500;
    private static final long NOT_EXISTING_GAME_ID = 999_999L;

    @Autowired
    private GameSeatCreateService gameSeatCreateService;

    @Autowired
    private GameSeatRepository gameSeatRepository;

    @Autowired
    private PricePolicy pricePolicy;

    @Autowired
    private EntityManager entityManager;

    private Long gameIdWithSeats;
    private Long stadiumIdWithoutSeats;

    @BeforeEach
    void setUp() {
        Stadium stadiumWithSeats = persistStadium("테스트구장");
        SeatZone zone = persistSeatZone(stadiumWithSeats, "내야존", SeatGrade.INFIELD, 15000);
        persistActiveSeats(stadiumWithSeats, zone, EXPECTED_SEAT_COUNT);

        Team homeTeam = persistTeam("테스트홈팀", stadiumWithSeats);
        Team awayTeam = persistTeam("테스트원정팀", stadiumWithSeats);

        Game gameWithSeats = persistGame(homeTeam, awayTeam, stadiumWithSeats);
        gameIdWithSeats = gameWithSeats.getId();

        // 좌석을 하나도 만들지 않은 구장 — "좌석 0건" 테스트 전용
        Stadium stadiumWithoutSeats = persistStadium("좌석없는구장");
        stadiumIdWithoutSeats = stadiumWithoutSeats.getId();
    }

    @DisplayName("재고를 오픈하면 구장 좌석 수만큼 GameSeat이 생성된다")
    @Test
    void should_create500GameSeats_when_openInventory() {
        // when
        GameSeatOpenResponse response = gameSeatCreateService.openInventory(gameIdWithSeats);

        // then
        assertThat(response.gameId()).isEqualTo(gameIdWithSeats);
        assertThat(response.createdCount()).isEqualTo(EXPECTED_SEAT_COUNT);
        assertThat(gameSeatRepository.existsByGameId(gameIdWithSeats)).isTrue();
    }

    @DisplayName("생성된 GameSeat은 AVAILABLE 상태이고 version이 0이다")
    @Test
    void should_setAvailableStatusAndZeroVersion_when_openInventory() {
        // given
        gameSeatCreateService.openInventory(gameIdWithSeats);
        entityManager.flush(); // INSERT를 DB로 밀어 version 채번을 확정한다

        // when
        List<GameSeat> gameSeats = findGameSeats(gameIdWithSeats);

        // then
        assertThat(gameSeats).hasSize(EXPECTED_SEAT_COUNT);
        assertThat(gameSeats).allSatisfy(gameSeat -> {
            assertThat(gameSeat.getStatus()).isEqualTo(GameSeatStatus.AVAILABLE);
            assertThat(gameSeat.getVersion()).isZero();
            assertThat(gameSeat.getHoldExpiresAt()).isNull();
            assertThat(gameSeat.getSoldAt()).isNull();
        });
    }

    /**
     * 기대값을 상수로 박지 않고 정책에 다시 물어본다.
     * <p>
     * 서비스가 PricePolicy를 올바른 인자로
     * (경기 일시 · 구역 등급 · 구역 기본가) 호출했는가를 검증한다.
     */
    @DisplayName("생성된 GameSeat의 가격은 PricePolicy 산정값과 일치한다")
    @Test
    void should_setPriceByPricePolicy_when_openInventory() {
        // given
        Game game = entityManager.find(Game.class, gameIdWithSeats);
        gameSeatCreateService.openInventory(gameIdWithSeats);
        entityManager.flush();

        // when
        List<GameSeat> gameSeats = findGameSeats(gameIdWithSeats);

        // then
        assertThat(gameSeats).allSatisfy(gameSeat -> {
            int expectedPrice
                = pricePolicy
                    .calculate(
                        game.getGameAt(),
                        gameSeat.getSeat().getZone().getGrade(),
                        gameSeat.getSeat().getZone().getBasePrice()
                    );
            assertThat(gameSeat.getPrice()).isEqualTo(expectedPrice);
        });
    }

    @DisplayName("존재하지 않는 경기의 재고를 오픈하면 GameNotFoundException이 발생한다")
    @Test
    void should_throwGameNotFound_when_gameIdNotExists() {
        // when & then
        assertThatThrownBy(() -> gameSeatCreateService.openInventory(NOT_EXISTING_GAME_ID))
            .isInstanceOf(GameNotFoundException.class);
    }

    @DisplayName("이미 재고가 오픈된 경기를 재호출하면 SeatInventoryAlreadyOpenedException이 발생한다")
    @Test
    void should_throwAlreadyOpened_when_calledTwice() {
        // given
        gameSeatCreateService.openInventory(gameIdWithSeats);

        // when & then — 애플리케이션 레벨 1차 방어선이 동작한다.
        // (DB 유니크 제약까지 가지 않고 409로 걸린다)
        assertThatThrownBy(() -> gameSeatCreateService.openInventory(gameIdWithSeats))
            .isInstanceOf(SeatInventoryAlreadyOpenedException.class);
    }

    @DisplayName("좌석이 없는 구장의 경기는 기준 데이터 결함으로 IllegalStateException이 발생한다")
    @Test
    void should_throwIllegalState_when_stadiumHasNoSeat() {
        // given
        Stadium stadiumWithoutSeats = entityManager.find(Stadium.class, stadiumIdWithoutSeats);
        Team homeTeam = persistTeam("좌석없음홈팀", stadiumWithoutSeats);
        Team awayTeam = persistTeam("좌석없음원정팀", stadiumWithoutSeats);
        Long gameIdWithoutSeats = persistGame(homeTeam, awayTeam, stadiumWithoutSeats).getId();

        // when & then
        assertThatThrownBy(() -> gameSeatCreateService.openInventory(gameIdWithoutSeats))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("활성 좌석이 없습니다");
    }

    private Team persistTeam(String name, Stadium homeStadium) {
        Team team = Team.of(name, homeStadium);
        entityManager.persist(team);
        return team;
    }

    private Stadium persistStadium(String name) {
        Stadium stadium = Stadium.of(name, "테스트주소", 30000);
        entityManager.persist(stadium);
        return stadium;
    }

    private SeatZone persistSeatZone(Stadium stadium, String name, SeatGrade grade, int basePrice) {
        SeatZone zone = SeatZone.of(stadium, name, grade, basePrice);
        entityManager.persist(zone);
        return zone;
    }

    private void persistActiveSeats(Stadium stadium, SeatZone zone, int count) {
        // uk_seats_location(stadium_id, zone_id, seat_block, seat_row, seat_number) 유니크 제약을 만족하도록
        // 블록 25석 단위로 행(row)을 나눠 조합이 겹치지 않게 한다.
        for (int i = 0; i < count; i++) {
            String seatRow = String.valueOf((i / 25) + 1);
            String seatNumber = String.valueOf((i % 25) + 1);
            Seat seat = Seat.of(stadium, zone, "A", seatRow, seatNumber);
            entityManager.persist(seat);
        }
    }

    private Game persistGame(Team homeTeam, Team awayTeam, Stadium stadium) {
        LocalDateTime gameAt = LocalDateTime.now().plusDays(1);
        Game game
            = Game
                .builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium(stadium)
                .gameAt(gameAt)
                .bookingOpenAt(gameAt.minusDays(7))
                .bookingCloseAt(gameAt.minusHours(1))
                .bookingStatus(BookingStatus.SCHEDULED)
                .build();
        entityManager.persist(game);
        return game;
    }

    private List<GameSeat> findGameSeats(Long gameId) {
        return entityManager.createQuery("""
            select gs
            from GameSeat gs
            join fetch gs.seat s
            join fetch s.zone
            where gs.game.id = :gameId
            """, GameSeat.class).setParameter("gameId", gameId).getResultList();
    }
}
