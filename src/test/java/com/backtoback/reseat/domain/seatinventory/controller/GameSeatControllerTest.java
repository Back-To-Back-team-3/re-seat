package com.backtoback.reseat.domain.seatinventory.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.service.GameSeatCreateService;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * 경기 좌석 현황·구역 요약 조회 API 통합 테스트.
 */
@Disabled("테스트제외")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GameSeatControllerTest {

    private static final long SEEDED_STADIUM_ID = 1L;
    private static final String SEATS_URI = "/api/v1/games/{gameId}/seats";
    private static final String ZONES_URI = "/api/v1/games/{gameId}/zones";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private GameSeatCreateService gameSeatCreateService;

    private Long gameIdWithSeats;

    @BeforeEach
    void setUp() {
        gameIdWithSeats = findFirstGameIdOfStadium();
        gameSeatCreateService.openInventory(gameIdWithSeats);
    }

    // ---------- GET /seats ----------

    @DisplayName("필터 없이 조회하면 500건을 반환한다")
    @WithMockUser
    @Test
    void should_return200AndAllSeats_when_noFilter() throws Exception {
        mockMvc.perform(get(SEATS_URI, gameIdWithSeats))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(500));
    }

    @DisplayName("zoneId로 필터링하면 해당 구역의 좌석만 반환한다")
    @WithMockUser
    @Test
    void should_filterByZone_when_zoneIdGiven() throws Exception {
        Long zoneId = findFirstZoneIdOfGame(gameIdWithSeats);

        mockMvc.perform(get(SEATS_URI, gameIdWithSeats).param("zoneId", String.valueOf(zoneId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].zoneId").value(zoneId))
            .andExpect(jsonPath("$.data.length()").value(50)); // V4 시드 기준 구역당 50석
    }

    @DisplayName("grade로 필터링하면 해당 등급의 좌석만 반환한다")
    @WithMockUser
    @Test
    void should_filterByGrade_when_gradeGiven() throws Exception {
        mockMvc.perform(get(SEATS_URI, gameIdWithSeats).param("grade", SeatGrade.INFIELD.name()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[*].grade")
                .value(org.hamcrest.Matchers.everyItem(
                    org.hamcrest.Matchers.is(SeatGrade.INFIELD.name()))))
            .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @DisplayName("status로 필터링하면 해당 상태의 좌석만 반환한다")
    @WithMockUser
    @Test
    void should_filterByStatus_when_statusGiven() throws Exception {
        // given: 좌석 일부를 SOLD로 바꿔 AVAILABLE/SOLD가 섞인 상태를 만든다
        markSomeSeatsSold(gameIdWithSeats);

        mockMvc.perform(get(SEATS_URI, gameIdWithSeats).param("status", GameSeatStatus.SOLD.name()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(10))
            .andExpect(jsonPath("$.data[*].status")
                .value(org.hamcrest.Matchers.everyItem(
                    org.hamcrest.Matchers.is(GameSeatStatus.SOLD.name()))));
    }

    // 헬퍼: N건만 SOLD로 변경 (기존 markAllSeatsSold와 구분)
    private void markSomeSeatsSold(Long gameId) {
        List<Long> targetIds = entityManager.createQuery(
                "SELECT gs.id FROM GameSeat gs WHERE gs.game.id = :gameId ORDER BY gs.id ASC",
                Long.class)
            .setParameter("gameId", gameId)
            .setMaxResults(10)
            .getResultList();

        entityManager.createQuery("UPDATE GameSeat gs SET gs.status = :status WHERE gs.id IN :ids")
            .setParameter("status", GameSeatStatus.SOLD)
            .setParameter("ids", targetIds)
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    @DisplayName("존재하지 않는 경기를 조회하면 404 GAME_NOT_FOUND를 반환한다")
    @WithMockUser
    @Test
    void should_return404_when_gameNotFound() throws Exception {
        mockMvc.perform(get(SEATS_URI, 999_999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @DisplayName("재고가 오픈되지 않은 경기를 조회하면 409 SEAT_INVENTORY_NOT_OPENED를 반환한다")
    @WithMockUser
    @Test
    void should_return409_when_inventoryNotOpened() throws Exception {
        Long gameIdWithoutInventory = findFirstGameIdOfDifferentStadium();

        mockMvc.perform(get(SEATS_URI, gameIdWithoutInventory))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("SEAT_INVENTORY_NOT_OPENED"));
    }

    // ---------- GET /zones ----------

    @DisplayName("구역 요약을 조회하면 전 구역이 반환된다")
    @WithMockUser
    @Test
    void should_return200AndAllZones_when_getZoneSummaries() throws Exception {
        mockMvc.perform(get(ZONES_URI, gameIdWithSeats))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].totalCount").value(50));
    }

    @DisplayName("전량 매진된 구역은 availableCount가 0으로 집계된다")
    @WithMockUser
    @Test
    void should_returnZeroAvailable_when_allSold() throws Exception {
        // given: 선점 로직이 아직 없으므로 테스트에서 직접 상태를 SOLD로 변경
        markAllSeatsSold(gameIdWithSeats);

        mockMvc.perform(get(ZONES_URI, gameIdWithSeats))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].availableCount").value(0));
    }

    @DisplayName("존재하지 않는 경기의 구역 요약을 조회하면 404를 반환한다")
    @WithMockUser
    @Test
    void should_return404_when_gameNotFoundForZones() throws Exception {
        mockMvc.perform(get(ZONES_URI, 999_999L))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @DisplayName("재고가 오픈되지 않은 경기의 구역 요약을 조회하면 409를 반환한다")
    @WithMockUser
    @Test
    void should_return409_when_inventoryNotOpenedForZones() throws Exception {
        Long gameIdWithoutInventory = findFirstGameIdOfDifferentStadium();

        mockMvc.perform(get(ZONES_URI, gameIdWithoutInventory))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("SEAT_INVENTORY_NOT_OPENED"));
    }

    // ---------- 인증 없이 접근 시 ----------

    @DisplayName("인증 없이 접근하면 401을 반환한다")
    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(get(SEATS_URI, gameIdWithSeats))
            .andExpect(status().isUnauthorized());
    }

    // ---------- 테스트 헬퍼 ----------

    /**
     * stadium_id가 일치하는 경기 중 가장 작은 id를 조회한다.
     * gameId 하드코딩을 피하기 위한 헬퍼.
     */
    private Long findFirstGameIdOfStadium() {
        return entityManager.createQuery(
                "SELECT g.id FROM Game g WHERE g.stadium.id = :stadiumId ORDER BY g.id ASC",
                Long.class)
            .setParameter("stadiumId", SEEDED_STADIUM_ID)
            .setMaxResults(1)
            .getSingleResult();
    }

    /**
     * 재고가 열려 있는 stadium(=1) 이외 구장의 경기 id를 조회한다.
     * 재고 미오픈(409) 케이스 검증용.
     */
    private Long findFirstGameIdOfDifferentStadium() {
        return entityManager.createQuery(
                """
                    SELECT g.id FROM Game g
                    WHERE NOT EXISTS (
                        SELECT 1 FROM GameSeat gs WHERE gs.game.id = g.id
                    )
                    ORDER BY g.id ASC
                    """,
                Long.class)
            .setMaxResults(1)
            .getSingleResult();
    }

    private Long findFirstZoneIdOfGame(Long gameId) {
        return entityManager.createQuery(
                "SELECT gs.seat.zone.id FROM GameSeat gs WHERE gs.game.id = :gameId ORDER BY gs.id ASC",
                Long.class)
            .setParameter("gameId", gameId)
            .setMaxResults(1)
            .getSingleResult();
    }

    /**
     * 좌석 선점 로직이 아직 없으므로, 매진 케이스 검증을 위해
     * 테스트에서 직접 game_seats 상태를 SOLD로 bulk update한다.
     */
    private void markAllSeatsSold(Long gameId) {
        Query query = entityManager.createQuery(
            "UPDATE GameSeat gs SET gs.status = :status WHERE gs.game.id = :gameId");
        query.setParameter("status", GameSeatStatus.SOLD);
        query.setParameter("gameId", gameId);
        query.executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
