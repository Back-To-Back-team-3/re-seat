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
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
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
 * 재고 오픈 API 통합 및 인가 테스트.
 * <p>test 프로필(H2)에는 시드 데이터가 없으므로,
 * OPEN 전이의 선행 조건인 좌석 재고까지 각 테스트 실행 전에 직접 준비한다.
 * (Stadium → SeatZone → Seat → Team → Game 순)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminGameSeatControllerTest {

    private static final long NOT_EXISTING_GAME_ID = 999_999L;
    private static final String OPEN_INVENTORY_URI = "/api/v1/admin/games/{gameId}/seats";
    // 이 테스트에서 직접 심은 좌석 수. GameSeatOpenResponse.createdCount 검증 기준이다.
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

    private Long gameIdWithSeats;

    @BeforeEach
    void setUp() {
        Stadium stadium = stadiumRepository.save(Stadium.of("테스트 구장", "서울시 테스트구", 10_000));
        Team homeTeam = teamRepository.save(Team.of("홈팀", stadium));
        Team awayTeam = teamRepository.save(Team.of("원정팀", stadium));

        // openInventory()가 구장의 활성 좌석 전체를 대상으로 재고를 생성하므로,
        // createdCount 단언과 숫자를 맞추기 위해 좌석 수를 상수(SEEDED_SEAT_COUNT)로 고정한다.
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

        gameIdWithSeats = gameRepository.save(game).getId();
    }

    @DisplayName("ADMIN이 재고 오픈을 요청하면 201 Created와 생성 결과를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return201_when_adminOpensInventory() throws Exception {
        mockMvc
            .perform(post(OPEN_INVENTORY_URI, gameIdWithSeats))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.gameId").value(gameIdWithSeats))
            .andExpect(jsonPath("$.data.createdCount").value(SEEDED_SEAT_COUNT))
            .andExpect(jsonPath("$.data.priceRange.min").isNumber())
            .andExpect(jsonPath("$.data.priceRange.max").isNumber());
    }

    @DisplayName("미인증 사용자는 401을 받는다")
    @WithAnonymousUser
    @Test
    void should_return401_when_anonymousUser() throws Exception {
        mockMvc.perform(post(OPEN_INVENTORY_URI, gameIdWithSeats)).andDo(print()).andExpect(status().isUnauthorized());
    }

    @DisplayName("ADMIN이 아닌 일반 사용자는 403을 받는다")
    @WithMockUser(roles = "USER")
    @Test
    void should_return403_when_normalUser() throws Exception {
        mockMvc.perform(post(OPEN_INVENTORY_URI, gameIdWithSeats)).andDo(print()).andExpect(status().isForbidden());
    }

    @DisplayName("존재하지 않는 경기는 404 GAME_NOT_FOUND를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return404_when_gameNotFound() throws Exception {
        mockMvc
            .perform(post(OPEN_INVENTORY_URI, NOT_EXISTING_GAME_ID))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @DisplayName("이미 오픈된 경기를 재호출하면 409 SEAT_INVENTORY_ALREADY_OPENED를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return409_when_calledTwice() throws Exception {
        // given
        mockMvc.perform(post(OPEN_INVENTORY_URI, gameIdWithSeats)).andExpect(status().isCreated());

        // when & then
        mockMvc
            .perform(post(OPEN_INVENTORY_URI, gameIdWithSeats))
            .andDo(print())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("SEAT_INVENTORY_ALREADY_OPENED"));
    }
}
