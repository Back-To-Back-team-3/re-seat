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

/**
 * 경기 예매 상태 전이 API 통합 및 인가 테스트.
 * <p>ApplicationContext 로딩만 통과시키기 위해 RedissonClient를 목(mock)으로 대체한다.
 * (RedissonClient 자체의 동작은 이 API와 무관하므로 목의 실제 동작은 검증하지 않는다.)
 * <p>test 프로필(H2)에는 시드 데이터가 없으므로,
 * OPEN 전이의 선행 조건인 좌석 재고까지 각 테스트 실행 전에 직접 준비한다.
 * (Stadium → SeatZone → Seat → Team → Game 순)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminGameBookingControllerTest {

    private static final long NOT_EXISTING_GAME_ID = 999_999L;
    private static final String BOOKING_STATUS_URI = "/api/v1/admin/games/{gameId}/booking-status";

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

    private Long scheduledGameId;

    @BeforeEach
    void setUp() {
        Stadium stadium = stadiumRepository.save(Stadium.of("테스트 구장", "서울시 테스트구", 10_000));
        Team homeTeam = teamRepository.save(Team.of("홈팀", stadium));
        Team awayTeam = teamRepository.save(Team.of("원정팀", stadium));

        // GameSeatCreateService.openInventory()가 요구하는 최소 좌석 재고 준비
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

        scheduledGameId = gameRepository.save(game).getId();
    }

    private String body(String bookingStatus, String reason) {
        return String.format("{\"bookingStatus\":\"%s\",\"reason\":\"%s\"}", bookingStatus, reason);
    }

    @DisplayName("좌석 재고가 열려 있으면 ADMIN은 SCHEDULED 경기를 OPEN으로 전이할 수 있다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return200_when_adminOpensScheduledGameWithSeatsReady() throws Exception {
        // given — OPEN 전이의 선행 조건인 좌석 재고를 먼저 만든다
        gameSeatCreateService.openInventory(scheduledGameId);

        // when & then
        mockMvc
            .perform(
                patch(BOOKING_STATUS_URI, scheduledGameId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("OPEN", "예매 오픈 시각 도달"))
            )
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.gameId").value(scheduledGameId))
            .andExpect(jsonPath("$.data.bookingStatus").value("OPEN"));
    }

    @DisplayName("미인증 사용자는 401을 받는다")
    @WithAnonymousUser
    @Test
    void should_return401_when_anonymousUser() throws Exception {
        mockMvc
            .perform(
                patch(BOOKING_STATUS_URI, scheduledGameId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("OPEN", "사유"))
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
                patch(BOOKING_STATUS_URI, scheduledGameId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("OPEN", "사유"))
            )
            .andDo(print())
            .andExpect(status().isForbidden());
    }

    @DisplayName("존재하지 않는 경기는 404 GAME_NOT_FOUND를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return404_when_gameNotFound() throws Exception {
        mockMvc
            .perform(
                patch(BOOKING_STATUS_URI, NOT_EXISTING_GAME_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("OPEN", "사유"))
            )
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @DisplayName("허용되지 않은 bookingStatus 값은 400 INVALID_REQUEST를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return400_when_bookingStatusInvalid() throws Exception {
        mockMvc
            .perform(
                patch(BOOKING_STATUS_URI, scheduledGameId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("SUSPENDED", "사유"))
            )
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @DisplayName("좌석 재고가 없는 경기를 OPEN 요청하면 409 SEAT_INVENTORY_NOT_OPENED를 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return409_when_seatInventoryNotOpened() throws Exception {
        mockMvc
            .perform(
                patch(BOOKING_STATUS_URI, scheduledGameId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("OPEN", "사유"))
            )
            .andDo(print())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("SEAT_INVENTORY_NOT_OPENED"));
    }

    @DisplayName("이미 CLOSED인 경기를 OPEN으로 재전이하면 409 INVALID_BOOKING_STATUS_TRANSITION을 반환한다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return409_when_invalidTransition() throws Exception {
        // given — SCHEDULED → OPEN → CLOSED까지 미리 전이시켜 재오픈 불가 상태를 만든다
        gameSeatCreateService.openInventory(scheduledGameId);
        gameRepository.compareAndSetBookingStatus(scheduledGameId, BookingStatus.SCHEDULED, BookingStatus.OPEN);
        gameRepository.compareAndSetBookingStatus(scheduledGameId, BookingStatus.OPEN, BookingStatus.CLOSED);

        mockMvc
            .perform(
                patch(BOOKING_STATUS_URI, scheduledGameId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("OPEN", "사유"))
            )
            .andDo(print())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorCode").value("INVALID_BOOKING_STATUS_TRANSITION"));
    }
}
