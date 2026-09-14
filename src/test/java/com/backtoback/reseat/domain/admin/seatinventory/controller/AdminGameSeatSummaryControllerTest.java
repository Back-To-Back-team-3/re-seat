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

/**
 * 경기 좌석 상태 요약 조회 API 통합 테스트.
 * <p>test 프로필(H2)에는 시드 데이터가 없으므로 구장·구역·좌석·구단·경기를 직접 준비한다.
 * "재고 미오픈" 케이스를 위해 재고를 오픈하지 않은 경기도 하나 더 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminGameSeatSummaryControllerTest {

    private static final long NOT_EXISTING_GAME_ID = 999_999L;
    private static final String SUMMARY_URI = "/api/v1/admin/games/{gameId}/seats/summary";

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

    private Long gameId;
    private Long gameIdWithoutInventory;

    @BeforeEach
    void setUp() {
        Stadium stadium = stadiumRepository.save(Stadium.of("테스트 구장", "서울시 테스트구", 10_000));
        Team homeTeam = teamRepository.save(Team.of("홈팀", stadium));
        Team awayTeam = teamRepository.save(Team.of("원정팀", stadium));

        SeatZone zone = seatZoneRepository.save(SeatZone.of(stadium, "테스트 구역", SeatGrade.INFIELD, 18_000));
        seatRepository.save(Seat.of(stadium, zone, "A", "1", "1"));
        seatRepository.save(Seat.of(stadium, zone, "A", "1", "2"));

        LocalDateTime now = LocalDateTime.now();
        gameId = createGame(homeTeam, awayTeam, stadium, now).getId();
        gameSeatCreateService.openInventory(gameId);

        // "재고 미오픈" 케이스 전용 — 위와 같은 구장이지만 오픈하지 않는다
        gameIdWithoutInventory = createGame(homeTeam, awayTeam, stadium, now.plusHours(3)).getId();
    }

    private Game createGame(Team homeTeam, Team awayTeam, Stadium stadium, LocalDateTime gameAt) {
        Game game
            = Game
                .builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium(stadium)
                .gameAt(gameAt.plusDays(7))
                .bookingOpenAt(gameAt)
                .bookingCloseAt(gameAt.plusDays(7))
                .bookingStatus(BookingStatus.SCHEDULED)
                .title("테스트 경기")
                .build();
        return gameRepository.save(game);
    }

    @DisplayName("ADMIN이 요약 조회를 요청하면 200과 상태별 합계를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return200AndCounts_when_adminRequestsSummary() throws Exception {
        mockMvc
            .perform(get(SUMMARY_URI, gameId))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.available").value(2))
            .andExpect(jsonPath("$.data.held").value(0))
            .andExpect(jsonPath("$.data.sold").value(0))
            .andExpect(jsonPath("$.data.blocked").value(0));
    }

    @DisplayName("재고가 오픈되지 않은 경기를 요약 조회하면 409를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return409_when_inventoryNotOpened() throws Exception {
        mockMvc
            .perform(get(SUMMARY_URI, gameIdWithoutInventory))
            .andDo(print())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("SEAT_INVENTORY_NOT_OPENED"));
    }

    @DisplayName("존재하지 않는 경기는 404를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return404_when_gameNotFound() throws Exception {
        mockMvc
            .perform(get(SUMMARY_URI, NOT_EXISTING_GAME_ID))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @DisplayName("ADMIN이 아닌 사용자는 403을 받는다")
    @WithMockUser(roles = "USER")
    @Test
    void should_return403_when_normalUser() throws Exception {
        mockMvc.perform(get(SUMMARY_URI, gameId)).andDo(print()).andExpect(status().isForbidden());
    }
}
