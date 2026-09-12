package com.backtoback.reseat.domain.admin.game.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.stadium.repository.StadiumRepository;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.team.repository.TeamRepository;

/**
 * 관리자 경기 등록 API 통합 및 인가 테스트.
 * <p>등록 API는 좌석 재고 오픈와 책임이 분리되어 있으므로, 좌석 재고(SeatZone/Seat) 준비 없이 검증한다.
 * <p>compareAndSetBookingStatus 같은 경합 로직이 없어 동시성 테스트는 별도로 두지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminGameRegisterControllerTest {

    private static final long NOT_EXISTING_ID = 999_999L;
    private static final String REGISTER_URI = "/api/v1/admin/games";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StadiumRepository stadiumRepository;

    @Autowired
    private TeamRepository teamRepository;

    private Long stadiumId;
    private Long homeTeamId;
    private Long awayTeamId;

    @BeforeEach
    void setUp() {
        Stadium stadium = stadiumRepository.save(Stadium.of("테스트 구장", "서울시 테스트구", 10_000));
        stadiumId = stadium.getId();

        homeTeamId = teamRepository.save(Team.of("홈팀", stadium)).getId();
        awayTeamId = teamRepository.save(Team.of("원정팀", stadium)).getId();
    }

    private String body(
        Long stadiumId,
        Long homeTeamId,
        Long awayTeamId,
        LocalDateTime gameAt,
        LocalDateTime bookingOpenAt,
        LocalDateTime bookingCloseAt
    ) {
        return String
            .format(
                "{\"stadiumId\":%d,\"homeTeamId\":%d,\"awayTeamId\":%d,"
                    + "\"gameAt\":\"%s\",\"bookingOpenAt\":\"%s\",\"bookingCloseAt\":\"%s\",\"title\":\"테스트 경기\"}",
                stadiumId,
                homeTeamId,
                awayTeamId,
                gameAt.format(FORMATTER),
                bookingOpenAt.format(FORMATTER),
                bookingCloseAt.format(FORMATTER)
            );
    }

    @DisplayName("ADMIN은 정상 요청으로 SCHEDULED 상태의 경기를 등록할 수 있다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return201_when_adminRegistersGameWithValidRequest() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        mockMvc
            .perform(
                post(REGISTER_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(stadiumId, homeTeamId, awayTeamId, now.plusDays(7), now, now.plusDays(6)))
            )
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.bookingStatus").value("SCHEDULED"));
    }

    @DisplayName("미인증 사용자는 401을 받는다")
    @WithAnonymousUser
    @Test
    void should_return401_when_anonymousUser() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        mockMvc
            .perform(
                post(REGISTER_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(stadiumId, homeTeamId, awayTeamId, now.plusDays(7), now, now.plusDays(6)))
            )
            .andDo(print())
            .andExpect(status().isUnauthorized());
    }

    @DisplayName("ADMIN이 아닌 사용자는 403을 받는다")
    @WithMockUser(roles = "USER")
    @Test
    void should_return403_when_normalUser() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        mockMvc
            .perform(
                post(REGISTER_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(stadiumId, homeTeamId, awayTeamId, now.plusDays(7), now, now.plusDays(6)))
            )
            .andDo(print())
            .andExpect(status().isForbidden());
    }

    @DisplayName("홈팀과 원정팀이 동일하면 400 SAME_TEAM_MATCH를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return400_when_homeTeamEqualsAwayTeam() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        mockMvc
            .perform(
                post(REGISTER_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(stadiumId, homeTeamId, homeTeamId, now.plusDays(7), now, now.plusDays(6)))
            )
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("SAME_TEAM_MATCH"));
    }

    @DisplayName("예매 오픈 시각이 마감 시각보다 늦으면 400 INVALID_REQUEST를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return400_when_bookingOpenAtAfterBookingCloseAt() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        mockMvc
            .perform(
                post(REGISTER_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    // bookingOpenAt(now+6일)이 bookingCloseAt(now+1일)보다 늦은 잘못된 순서
                    .content(body(stadiumId, homeTeamId, awayTeamId, now.plusDays(7), now.plusDays(6), now.plusDays(1)))
            )
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @DisplayName("존재하지 않는 팀 ID로 요청하면 404 TEAM_NOT_FOUND를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return404_when_teamNotFound() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        mockMvc
            .perform(
                post(REGISTER_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(stadiumId, NOT_EXISTING_ID, awayTeamId, now.plusDays(7), now, now.plusDays(6)))
            )
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("TEAM_NOT_FOUND"));
    }

    @DisplayName("존재하지 않는 구장 ID로 요청하면 404 STADIUM_NOT_FOUND를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return404_when_stadiumNotFound() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        mockMvc
            .perform(
                post(REGISTER_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(NOT_EXISTING_ID, homeTeamId, awayTeamId, now.plusDays(7), now, now.plusDays(6)))
            )
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("STADIUM_NOT_FOUND"));
    }
}
