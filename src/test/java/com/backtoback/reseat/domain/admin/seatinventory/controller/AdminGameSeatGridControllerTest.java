package com.backtoback.reseat.domain.admin.seatinventory.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.admin.seatinventory.service.AdminGameSeatStatusService;
import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.seatinventory.service.GameSeatCreateService;
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
 * 관리자 전용 좌석 그리드 조회 API 통합 테스트.
 * <p>조회 로직 자체(필터·N+1 방지)는 SeatQueryServiceTest가 이미 검증하므로,
 * 여기서는 Queue-Token 없이 ADMIN 권한만으로 호출되는지에 집중한다.
 * <p>test 프로필(H2)에는 시드 데이터가 없으므로 구장·구역·좌석·구단·경기를 직접 준비한다.
 * <p>status 필터 검증은 시드 좌석을 실제로 BLOCKED로 전환해 상태가 섞인 상황에서 필터링이 진짜로 적용되는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminGameSeatGridControllerTest {

    private static final String GRID_URI = "/api/v1/admin/games/{gameId}/seats";
    // 이 테스트에서 직접 심은 좌석 수. 응답 배열 길이 검증 기준이다.
    private static final int SEEDED_SEAT_COUNT = 3;

    @Autowired
    private MockMvc mockMvc;

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
    private GameSeatCreateService gameSeatCreateService;

    @Autowired
    private AdminGameSeatStatusService adminGameSeatStatusService;

    @Autowired
    private EntityManager entityManager;

    private Long gameId;

    @BeforeEach
    void setUp() {
        Stadium stadium = stadiumRepository.save(Stadium.of("테스트 구장", "서울시 테스트구", 10_000));
        Team homeTeam = teamRepository.save(Team.of("홈팀", stadium));
        Team awayTeam = teamRepository.save(Team.of("원정팀", stadium));

        SeatZone zone = seatZoneRepository.save(SeatZone.of(stadium, "테스트 구역", SeatGrade.INFIELD, 18_000));
        for (int i = 1; i <= SEEDED_SEAT_COUNT; i++) {
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
        gameSeatCreateService.openInventory(gameId);
    }

    @DisplayName("ADMIN은 Queue-Token 헤더 없이도 좌석 그리드를 조회할 수 있다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return200_when_adminRequestsWithoutQueueToken() throws Exception {
        // Queue-Token 헤더를 의도적으로 전송하지 않는다 — 공개 API와의 핵심 차이 검증
        mockMvc
            .perform(get(GRID_URI, gameId))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(SEEDED_SEAT_COUNT));
    }

    @DisplayName("status 필터를 적용하면 해당 상태의 좌석만 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_returnFilteredSeats_when_statusParamGiven() throws Exception {
        // given — 좌석 1개를 BLOCKED로 바꿔 AVAILABLE/BLOCKED가 섞이게 만든다
        Long blockedGameSeatId = findFirstGameSeatId(gameId);
        adminGameSeatStatusService.blockSeat(blockedGameSeatId, "필터 테스트용 차단");

        mockMvc
            .perform(get(GRID_URI, gameId).param("status", "AVAILABLE"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(SEEDED_SEAT_COUNT - 1))
            .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"));

        mockMvc
            .perform(get(GRID_URI, gameId).param("status", "BLOCKED"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].status").value("BLOCKED"));

        // 시드 데이터엔 SOLD가 전혀 없으므로 SOLD 필터는 빈 배열을 반환해야 한다
        mockMvc
            .perform(get(GRID_URI, gameId).param("status", "SOLD"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @DisplayName("ADMIN이 아닌 사용자는 403을 받는다")
    @WithMockUser(roles = "USER")
    @Test
    void should_return403_when_normalUser() throws Exception {
        mockMvc.perform(get(GRID_URI, gameId)).andDo(print()).andExpect(status().isForbidden());
    }

    private Long findFirstGameSeatId(Long gameId) {
        return entityManager
            .createQuery("select gs.id from GameSeat gs where gs.game.id = :gameId order by gs.id asc", Long.class)
            .setParameter("gameId", gameId)
            .setMaxResults(1)
            .getSingleResult();
    }
}
