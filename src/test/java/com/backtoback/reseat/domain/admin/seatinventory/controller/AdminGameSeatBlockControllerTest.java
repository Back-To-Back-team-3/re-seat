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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
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

import jakarta.persistence.EntityManager;

/**
 * 좌석 판매 차단·해제 API 통합 및 인가 테스트.
 * <p>test 프로필(H2)에는 시드 데이터가 없으므로, 구장·구역·좌석·구단·경기를각 테스트 실행 전에 직접 준비한다.
 * 좌석 재고는 GameSeatCreateService.openInventory()로 오픈해 AVAILABLE 좌석 1건을 확보한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminGameSeatBlockControllerTest {

    private static final long NOT_EXISTING_GAME_SEAT_ID = 999_999L;
    private static final String BLOCK_URI = "/api/v1/admin/game-seats/{gameSeatId}/block";
    private static final String UNBLOCK_URI = "/api/v1/admin/game-seats/{gameSeatId}/unblock";

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
    private EntityManager entityManager;

    private Long availableGameSeatId;

    @BeforeEach
    void setUp() {
        Stadium stadium = stadiumRepository.save(Stadium.of("테스트 구장", "서울시 테스트구", 10_000));
        Team homeTeam = teamRepository.save(Team.of("홈팀", stadium));
        Team awayTeam = teamRepository.save(Team.of("원정팀", stadium));

        SeatZone zone = seatZoneRepository.save(SeatZone.of(stadium, "테스트 구역", SeatGrade.INFIELD, 18_000));
        seatRepository.save(Seat.of(stadium, zone, "A", "1", "1"));

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

        Long gameId = gameRepository.save(game).getId();
        gameSeatCreateService.openInventory(gameId);

        availableGameSeatId
            = entityManager
                .createQuery("select gs.id from GameSeat gs where gs.game.id = :gameId", Long.class)
                .setParameter("gameId", gameId)
                .getSingleResult();
    }

    @DisplayName("ADMIN이 AVAILABLE 좌석을 차단하면 200과 BLOCKED 상태를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return200AndBlocked_when_adminBlocksAvailableSeat() throws Exception {
        mockMvc
            .perform(
                post(BLOCK_URI, availableGameSeatId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\": \"매크로 의심 좌석 임시 차단\"}")
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.gameSeatId").value(availableGameSeatId))
            .andExpect(jsonPath("$.data.status").value("BLOCKED"));
    }

    @DisplayName("이미 BLOCKED인 좌석을 다시 차단하면 409를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return409_when_blockingAlreadyBlockedSeat() throws Exception {
        mockMvc
            .perform(
                post(BLOCK_URI, availableGameSeatId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\": \"1차 차단\"}")
            )
            .andExpect(status().isOk());

        mockMvc
            .perform(
                post(BLOCK_URI, availableGameSeatId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\": \"2차 차단 시도\"}")
            )
            .andDo(print())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"));
    }

    @DisplayName("BLOCKED 좌석을 해제하면 200과 AVAILABLE 상태를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return200AndAvailable_when_adminUnblocksBlockedSeat() throws Exception {
        mockMvc
            .perform(
                post(BLOCK_URI, availableGameSeatId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\": \"임시 차단\"}")
            )
            .andExpect(status().isOk());

        mockMvc
            .perform(
                post(UNBLOCK_URI, availableGameSeatId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\": \"차단 사유 해소\"}")
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @DisplayName("reason 누락 시 400 INVALID_REQUEST를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return400_when_reasonMissing() throws Exception {
        mockMvc
            .perform(post(BLOCK_URI, availableGameSeatId).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @DisplayName("존재하지 않는 좌석 차단 요청은 404를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return404_when_gameSeatNotFound() throws Exception {
        mockMvc
            .perform(
                post(BLOCK_URI, NOT_EXISTING_GAME_SEAT_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\": \"사유\"}")
            )
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("GAME_SEAT_NOT_FOUND"));
    }

    @DisplayName("미인증 사용자는 401을 받는다")
    @WithAnonymousUser
    @Test
    void should_return401_when_anonymousUser() throws Exception {
        mockMvc
            .perform(
                post(BLOCK_URI, availableGameSeatId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\": \"사유\"}")
            )
            .andDo(print())
            .andExpect(status().isUnauthorized());
    }

    @DisplayName("ADMIN이 아닌 사용자는 403을 받는다")
    @WithMockUser(roles = "USER")
    @Test
    void should_return403_when_normalUser() throws Exception {
        mockMvc
            .perform(
                post(BLOCK_URI, availableGameSeatId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\": \"사유\"}")
            )
            .andDo(print())
            .andExpect(status().isForbidden());
    }
}
