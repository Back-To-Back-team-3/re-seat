package com.backtoback.reseat.domain.seatinventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.seatinventory.dto.GameSeatOpenResponse;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.exception.SeatInventoryAlreadyOpenedException;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;
import com.backtoback.reseat.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좌석 재고 생성 서비스 통합 테스트.
 */
@SpringBootTest
@Transactional
@Import(QuerydslConfig.class)
class GameSeatCreateServiceTest {

    private static final long SEEDED_STADIUM_ID = 1L;
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

    @BeforeEach
    void setUp() {
        gameIdWithSeats = findFirstGameIdOfStadium(SEEDED_STADIUM_ID);
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
        entityManager.flush();  // INSERT를 DB로 밀어 version 채번을 확정한다

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
            int expectedPrice = pricePolicy.calculate(
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
        //               (DB 유니크 제약까지 가지 않고 409로 걸린다)
        assertThatThrownBy(() -> gameSeatCreateService.openInventory(gameIdWithSeats))
            .isInstanceOf(SeatInventoryAlreadyOpenedException.class);
    }

    /**
     * 좌석 기준 데이터가 없는 구장(stadium_id != 1)의 경기.
     * 현재 시드에서는 110건 중 88건이 여기에 해당한다.
     */
    @DisplayName("좌석이 없는 구장의 경기는 기준 데이터 결함으로 IllegalStateException이 발생한다")
    @Test
    void should_throwIllegalState_when_stadiumHasNoSeat() {
        // given
        Long gameIdWithoutSeats = entityManager.createQuery(
                "select g.id from Game g where g.stadium.id <> :stadiumId order by g.id asc", Long.class)
            .setParameter("stadiumId", SEEDED_STADIUM_ID)
            .setMaxResults(1)
            .getSingleResult();

        // when & then
        assertThatThrownBy(() -> gameSeatCreateService.openInventory(gameIdWithoutSeats))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("활성 좌석이 없습니다");
    }

    private Long findFirstGameIdOfStadium(Long stadiumId) {
        return entityManager.createQuery(
                "select g.id from Game g where g.stadium.id = :stadiumId order by g.id asc", Long.class)
            .setParameter("stadiumId", stadiumId)
            .setMaxResults(1)
            .getSingleResult();
    }

    private List<GameSeat> findGameSeats(Long gameId) {
        return entityManager.createQuery("""
                        select gs
                        from GameSeat gs
                        join fetch gs.seat s
                        join fetch s.zone
                        where gs.game.id = :gameId
                        """, GameSeat.class)
            .setParameter("gameId", gameId)
            .getResultList();
    }
}
