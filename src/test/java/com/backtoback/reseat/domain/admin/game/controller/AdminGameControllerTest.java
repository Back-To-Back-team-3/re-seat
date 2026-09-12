package com.backtoback.reseat.domain.admin.game.controller;

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
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.stadium.repository.StadiumRepository;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.team.repository.TeamRepository;

/**
 * 관리자 경기 목록 조회·검색 API 통합 및 인가 테스트.
 * <p>공개 조회(GameControllerTest)와 달리 ROLE_ADMIN 인가와 stadiumId 조건까지 검증한다.
 * 상태 전이(PATCH)는 AdminGameBookingControllerTest가 담당하므로 이 클래스 범위에서 제외한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminGameControllerTest {

    private static final String GAMES_URI = "/api/v1/admin/games";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StadiumRepository stadiumRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private GameRepository gameRepository;

    private Long stadiumId;
    private Long otherStadiumId;

    @BeforeEach
    void setUp() {
        // 좌석 재고(SeatZone/Seat)는 조회 API와 무관하므로 준비하지 않는다.
        Stadium stadium = stadiumRepository.save(Stadium.of("테스트 구장", "서울시 테스트구", 10_000));
        Stadium otherStadium = stadiumRepository.save(Stadium.of("테스트 구장2", "부산시 테스트구", 10_000));
        stadiumId = stadium.getId();
        otherStadiumId = otherStadium.getId();

        Team homeTeam = teamRepository.save(Team.of("홈팀", stadium));
        Team awayTeam = teamRepository.save(Team.of("원정팀", stadium));
        Team otherTeam = teamRepository.save(Team.of("다른팀", otherStadium));

        LocalDateTime now = LocalDateTime.now();

        // stadiumId 구장에서 열리는 경기
        gameRepository
            .save(
                Game
                    .builder()
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .stadium(stadium)
                    .gameAt(now.plusDays(7))
                    .bookingOpenAt(now)
                    .bookingCloseAt(now.plusDays(7))
                    .bookingStatus(BookingStatus.SCHEDULED)
                    .title("테스트 경기")
                    .build()
            );

        // otherStadiumId 구장에서 열리는 경기 — stadiumId 필터링 검증용
        gameRepository
            .save(
                Game
                    .builder()
                    .homeTeam(otherTeam)
                    .awayTeam(homeTeam)
                    .stadium(otherStadium)
                    .gameAt(now.plusDays(7))
                    .bookingOpenAt(now)
                    .bookingCloseAt(now.plusDays(7))
                    .bookingStatus(BookingStatus.SCHEDULED)
                    .title("다른 구장 경기")
                    .build()
            );
    }

    @DisplayName("ADMIN은 조건 없이 전체 경기 목록을 페이지네이션으로 조회할 수 있다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return200WithAllGames_when_noConditionGiven() throws Exception {
        mockMvc
            .perform(get(GAMES_URI))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @DisplayName("ADMIN은 stadiumId로 필터링된 경기 목록만 조회할 수 있다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_returnOnlyMatchingGames_when_stadiumIdGiven() throws Exception {
        mockMvc
            .perform(get(GAMES_URI).param("stadiumId", String.valueOf(stadiumId)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].stadium.stadiumId").value(stadiumId))
            .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @DisplayName("각 경기 응답에 bookingStatus·bookingOpenAt·bookingCloseAt이 포함된다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_includeBookingFields_when_searchGames() throws Exception {
        mockMvc
            .perform(get(GAMES_URI).param("stadiumId", String.valueOf(stadiumId)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].bookingStatus").value("SCHEDULED"))
            .andExpect(jsonPath("$.data.content[0].bookingOpenAt").exists())
            .andExpect(jsonPath("$.data.content[0].bookingCloseAt").exists());
    }

    @DisplayName("미인증 사용자는 401을 받는다")
    @WithAnonymousUser
    @Test
    void should_return401_when_anonymousUser() throws Exception {
        mockMvc.perform(get(GAMES_URI)).andDo(print()).andExpect(status().isUnauthorized());
    }

    @DisplayName("ADMIN이 아닌 사용자는 403을 받는다")
    @WithMockUser(roles = "USER")
    @Test
    void should_return403_when_normalUser() throws Exception {
        mockMvc.perform(get(GAMES_URI)).andDo(print()).andExpect(status().isForbidden());
    }
}
