package com.backtoback.reseat.domain.seatinventory.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.queue.service.AdmissionTokenService;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.service.GameSeatCreateService;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.global.security.CustomUserDetails;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * 경기 좌석 현황·구역 요약 조회 API 통합 테스트.
 * <p>좌석 목록 조회(필터 없음/zoneId/grade/status), 구역별 재고 요약 조회,
 * 존재하지 않는 경기(404)·재고 미오픈 경기(409)·미인증 요청(401) 처리를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GameSeatControllerTest {

    private static final String SEATS_URI = "/api/v1/games/{gameId}/seats";
    private static final String ZONES_URI = "/api/v1/games/{gameId}/zones";

    private static final int ZONE_COUNT = 10;
    private static final int SEATS_PER_ZONE = 50;

    /**
     * 컨트롤러가 요구하는 실제 타입(CustomUserDetails)으로 인증 컨텍스트를 채우기 위한 테스트 유저.
     */
    private static final CustomUserDetails TEST_USER = CustomUserDetails.of(1L, "seat-test@test.com", "USER");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private GameSeatCreateService gameSeatCreateService;

    @MockitoBean
    private AdmissionTokenService admissionTokenService;

    /**
     * 재고가 오픈된 경기 — 구역 10개 × 좌석 50개 = 500석. 필터·요약 조회 케이스 전반에서 쓴다.
     */
    private Long gameIdWithSeats;

    /**
     * 재고가 오픈되지 않은 경기 — 좌석·구역이 아예 없다. 409 케이스 전용.
     */
    private Long gameIdWithoutInventory;

    @BeforeEach
    void setUp() {
        // Queue-Token 검증은 이 테스트의 책임 범위 밖이므로 항상 통과시킨다.
        // 검증 로직 자체는 GameSeatControllerQueueTokenTest가 전담한다.
        doNothing().when(admissionTokenService).validateToken(anyLong(), anyLong(), any());

        // 1. 재고 오픈 대상 경기: 구역을 등급별로 절반씩 나눠 만든다.
        // grade=INFIELD 필터 테스트가 "1건 이상"을 요구하므로, 최소 하나 이상의 등급 조합이 필요하다.
        Stadium stadium = persistStadium("테스트구장");
        Team homeTeam = persistTeam("홈팀", stadium);
        Team awayTeam = persistTeam("원정팀", stadium);

        for (int z = 0; z < ZONE_COUNT; z++) {
            SeatGrade grade = (z % 2 == 0) ? SeatGrade.INFIELD : SeatGrade.OUTFIELD;
            SeatZone zone = persistSeatZone(stadium, "구역" + z, grade, 15000);
            persistSeats(stadium, zone, SEATS_PER_ZONE);
        }

        Game gameWithSeats = persistGame(homeTeam, awayTeam, stadium, BookingStatus.OPEN);
        gameIdWithSeats = gameWithSeats.getId();
        entityManager.flush();

        // 재고를 실제로 오픈해야 GameSeat이 생성된다 — 원래 setUp()과 동일한 순서.
        gameSeatCreateService.openInventory(gameIdWithSeats);
        entityManager.flush();
        entityManager.clear();

        // 2. 재고 미오픈 경기: 별도 구장에 좌석·구역 없이 경기만 만든다.
        // openInventory()를 호출하지 않으므로 GameSeat이 존재하지 않는 상태가 그대로 유지된다.
        Stadium stadiumWithoutInventory = persistStadium("재고미오픈구장");
        Team otherHomeTeam = persistTeam("팀A", stadiumWithoutInventory);
        Team otherAwayTeam = persistTeam("팀B", stadiumWithoutInventory);
        Game gameWithoutInventory
            = persistGame(otherHomeTeam, otherAwayTeam, stadiumWithoutInventory, BookingStatus.SCHEDULED);
        gameIdWithoutInventory = gameWithoutInventory.getId();
        entityManager.flush();
    }

    // ---------- GET /seats ----------

    @DisplayName("필터 없이 조회하면 500건을 반환한다")
    @Test
    void should_return200AndAllSeats_when_noFilter() throws Exception {
        mockMvc
            .perform(get(SEATS_URI, gameIdWithSeats).with(user(TEST_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(500));
    }

    @DisplayName("zoneId로 필터링하면 해당 구역의 좌석만 반환한다")
    @Test
    void should_filterByZone_when_zoneIdGiven() throws Exception {
        Long zoneId = findFirstZoneIdOfGame(gameIdWithSeats);

        mockMvc
            .perform(get(SEATS_URI, gameIdWithSeats).param("zoneId", String.valueOf(zoneId)).with(user(TEST_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].zoneId").value(zoneId))
            .andExpect(jsonPath("$.data.length()").value(SEATS_PER_ZONE));
    }

    @DisplayName("grade로 필터링하면 해당 등급의 좌석만 반환한다")
    @Test
    void should_filterByGrade_when_gradeGiven() throws Exception {
        mockMvc
            .perform(get(SEATS_URI, gameIdWithSeats).param("grade", SeatGrade.INFIELD.name()).with(user(TEST_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(
                jsonPath("$.data[*].grade")
                    .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(SeatGrade.INFIELD.name())))
            )
            .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @DisplayName("status로 필터링하면 해당 상태의 좌석만 반환한다")
    @Test
    void should_filterByStatus_when_statusGiven() throws Exception {
        // given: 좌석 일부를 SOLD로 바꿔 AVAILABLE/SOLD가 섞인 상태를 만든다
        markSomeSeatsSold(gameIdWithSeats);

        mockMvc
            .perform(get(SEATS_URI, gameIdWithSeats).param("status", GameSeatStatus.SOLD.name()).with(user(TEST_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(10))
            .andExpect(
                jsonPath("$.data[*].status")
                    .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(GameSeatStatus.SOLD.name())))
            );
    }

    // 헬퍼: N건만 SOLD로 변경 — markAllSeatsSold()와 동일한 이유(조회 API 책임 범위 유지)로 직접 bulk update한다.
    private void markSomeSeatsSold(Long gameId) {
        List<Long> targetIds
            = entityManager
                .createQuery("SELECT gs.id FROM GameSeat gs WHERE gs.game.id = :gameId ORDER BY gs.id ASC", Long.class)
                .setParameter("gameId", gameId)
                .setMaxResults(10)
                .getResultList();

        entityManager
            .createQuery("UPDATE GameSeat gs SET gs.status = :status WHERE gs.id IN :ids")
            .setParameter("status", GameSeatStatus.SOLD)
            .setParameter("ids", targetIds)
            .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    @DisplayName("존재하지 않는 경기를 조회하면 404 GAME_NOT_FOUND를 반환한다")
    @Test
    void should_return404_when_gameNotFound() throws Exception {
        mockMvc
            .perform(get(SEATS_URI, 999_999L).with(user(TEST_USER)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @DisplayName("재고가 오픈되지 않은 경기를 조회하면 409 SEAT_INVENTORY_NOT_OPENED를 반환한다")
    @Test
    void should_return409_when_inventoryNotOpened() throws Exception {
        mockMvc
            .perform(get(SEATS_URI, gameIdWithoutInventory).with(user(TEST_USER)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("SEAT_INVENTORY_NOT_OPENED"));
    }

    // ---------- GET /zones ----------

    @DisplayName("구역 요약을 조회하면 전 구역이 반환된다")
    @Test
    void should_return200AndAllZones_when_getZoneSummaries() throws Exception {
        mockMvc
            .perform(get(ZONES_URI, gameIdWithSeats).with(user(TEST_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].totalCount").value(SEATS_PER_ZONE));
    }

    @DisplayName("전량 매진된 구역은 availableCount가 0으로 집계된다")
    @Test
    void should_returnZeroAvailable_when_allSold() throws Exception {
        // given: 선점 로직이 아직 없으므로 테스트에서 직접 상태를 SOLD로 변경
        markAllSeatsSold(gameIdWithSeats);

        mockMvc
            .perform(get(ZONES_URI, gameIdWithSeats).with(user(TEST_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].availableCount").value(0));
    }

    @DisplayName("존재하지 않는 경기의 구역 요약을 조회하면 404를 반환한다")
    @Test
    void should_return404_when_gameNotFoundForZones() throws Exception {
        mockMvc
            .perform(get(ZONES_URI, 999_999L).with(user(TEST_USER)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @DisplayName("재고가 오픈되지 않은 경기의 구역 요약을 조회하면 409를 반환한다")
    @Test
    void should_return409_when_inventoryNotOpenedForZones() throws Exception {
        mockMvc
            .perform(get(ZONES_URI, gameIdWithoutInventory).with(user(TEST_USER)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("SEAT_INVENTORY_NOT_OPENED"));
    }

    // ---------- 인증 없이 접근 시 ----------

    @DisplayName("인증 없이 접근하면 401을 반환한다")
    @Test
    void should_return401_when_unauthenticated() throws Exception {
        // .with(user(...)) 없이 요청 — 인증 자체가 없는 상태를 재현한다.
        mockMvc.perform(get(SEATS_URI, gameIdWithSeats)).andExpect(status().isUnauthorized());
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

    /**
     * 좌석 {@code count}건을 만든다.
     * <p>{@code uk_seats_location(stadium_id, zone_id, seat_block, seat_row, seat_number)} 제약은 zone_id를 포함하므로,
     * 구역이 다르면 같은 block/row/number 조합을 재사용해도 충돌하지 않는다.
     */
    private void persistSeats(Stadium stadium, SeatZone zone, int count) {
        for (int i = 0; i < count; i++) {
            String seatRow = String.valueOf((i / 25) + 1);
            String seatNumber = String.valueOf((i % 25) + 1);
            Seat seat = Seat.of(stadium, zone, "A", seatRow, seatNumber);
            entityManager.persist(seat);
        }
    }

    private Game persistGame(Team homeTeam, Team awayTeam, Stadium stadium, BookingStatus bookingStatus) {
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
                .bookingStatus(bookingStatus)
                .build();
        entityManager.persist(game);
        return game;
    }

    // ---------- 조회용 헬퍼 ----------

    private Long findFirstZoneIdOfGame(Long gameId) {
        return entityManager
            .createQuery(
                "SELECT gs.seat.zone.id FROM GameSeat gs WHERE gs.game.id = :gameId ORDER BY gs.id ASC",
                Long.class
            )
            .setParameter("gameId", gameId)
            .setMaxResults(1)
            .getSingleResult();
    }

    /**
     * 매진 케이스 검증을 위해 game_seats 상태를 SOLD로 직접 bulk update한다.
     * 실제 선점(hold) 흐름을 거치지 않는 이유:
     * 이 테스트의 책임이 조회 API(zones 요약 집계) 검증이지 예약 도메인의 선점 로직 검증이 아니다.
     * 선점 흐름 자체는 reservation 도메인의 SeatHoldFacade 계열 테스트에서 별도로 검증한다.
     */
    private void markAllSeatsSold(Long gameId) {
        Query query
            = entityManager.createQuery("UPDATE GameSeat gs SET gs.status = :status WHERE gs.game.id = :gameId");
        query.setParameter("status", GameSeatStatus.SOLD);
        query.setParameter("gameId", gameId);
        query.executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
