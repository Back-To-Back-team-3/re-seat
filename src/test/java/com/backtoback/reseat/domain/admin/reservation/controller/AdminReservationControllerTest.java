package com.backtoback.reseat.domain.admin.reservation.controller;

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

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.stadium.repository.SeatRepository;
import com.backtoback.reseat.domain.stadium.repository.SeatZoneRepository;
import com.backtoback.reseat.domain.stadium.repository.StadiumRepository;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.team.repository.TeamRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;

/**
 * 관리자 예약·선점 상태 관리 목록 조회 API 통합 테스트.
 * <p>Mock 처리 없이 서비스·레포지토리를 실제로 붙여 H2 DB에서 검증한다.
 * remainingSeconds 계산 정확성은 AdminReservationQueryServiceTest가 담당하므로,
 * 이 테스트는 HTTP 상태 코드·응답 스키마·인가만 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminReservationControllerTest {

    private static final String URI_TEMPLATE = "/api/v1/admin/games/{gameId}/reservations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StadiumRepository stadiumRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private SeatZoneRepository seatZoneRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private GameSeatRepository gameSeatRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    private Long gameId;

    @BeforeEach
    void setUp() {
        // 구장 → 팀 → 경기
        Stadium stadium = stadiumRepository.save(Stadium.of("테스트 구장", "서울시 테스트구", 10_000));
        Team homeTeam = teamRepository.save(Team.of("홈팀", stadium));
        Team awayTeam = teamRepository.save(Team.of("원정팀", stadium));

        LocalDateTime now = LocalDateTime.now();
        Game game
            = gameRepository
                .save(
                    Game
                        .builder()
                        .homeTeam(homeTeam)
                        .awayTeam(awayTeam)
                        .stadium(stadium)
                        .gameAt(now.plusDays(7))
                        .bookingOpenAt(now)
                        .bookingCloseAt(now.plusDays(7))
                        .title("테스트 경기")
                        .build()
                );
        gameId = game.getId();

        // 좌석 계층: SeatZone → Seat → GameSeat (조회 응답의 seats 필드 검증에 필요)
        SeatZone zone = seatZoneRepository.save(SeatZone.of(stadium, "1루 블루석", SeatGrade.INFIELD, 18_000));
        Seat seatA = seatRepository.save(Seat.of(stadium, zone, "A", "3", "12"));
        Seat seatB = seatRepository.save(Seat.of(stadium, zone, "A", "3", "13"));

        GameSeat gameSeatA = gameSeatRepository.save(GameSeat.builder().game(game).seat(seatA).price(15_000).build());
        GameSeat gameSeatB = gameSeatRepository.save(GameSeat.builder().game(game).seat(seatB).price(15_000).build());

        User user = userRepository.save(User.builder().email("user1@test.com").name("홍길동").build());

        // HOLDING 예약 — 좌석 2건 (예약 1건 : 좌석 최대 2개 케이스 검증용)
        Reservation holding
            = reservationRepository
                .save(
                    Reservation
                        .builder()
                        .reservationNo("RSV-TEST-HOLDING")
                        .user(user)
                        .game(game)
                        .status(ReservationStatus.HOLDING)
                        .holdExpiresAt(now.plusMinutes(5))
                        .build()
                );
        reservationSeatRepository
            .save(ReservationSeat.builder().reservation(holding).gameSeat(gameSeatA).price(15_000).build());
        reservationSeatRepository
            .save(ReservationSeat.builder().reservation(holding).gameSeat(gameSeatB).price(15_000).build());

        // CONFIRMED 예약 — 좌석 없이 상태만 검증
        reservationRepository
            .save(
                Reservation
                    .builder()
                    .reservationNo("RSV-TEST-CONFIRMED")
                    .user(user)
                    .game(game)
                    .status(ReservationStatus.CONFIRMED)
                    .holdExpiresAt(now.minusMinutes(10))
                    .build()
            );

        // EXPIRED 예약 — 좌석 없음(만료 시 좌석은 이미 회수됐다고 가정)
        reservationRepository
            .save(
                Reservation
                    .builder()
                    .reservationNo("RSV-TEST-EXPIRED")
                    .user(user)
                    .game(game)
                    .status(ReservationStatus.EXPIRED)
                    .holdExpiresAt(now.minusMinutes(30))
                    .build()
            );
    }

    @DisplayName("status 없이 조회하면 전체 상태(HOLDING/CONFIRMED/EXPIRED) 예약이 반환된다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_returnAllReservations_when_noStatusGiven() throws Exception {
        mockMvc
            .perform(get(URI_TEMPLATE, gameId))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content.length()").value(3));
    }

    @DisplayName("status=HOLDING으로 조회하면 좌석 2건이 포함된다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_includeSeats_when_statusHolding() throws Exception {
        mockMvc
            .perform(get(URI_TEMPLATE, gameId).param("status", "HOLDING"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].status").value("HOLDING"))
            .andExpect(jsonPath("$.data.content[0].seats.length()").value(2));
    }

    @DisplayName("존재하지 않는 gameId로 조회하면 404 GAME_NOT_FOUND를 받는다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return404_when_gameNotFound() throws Exception {
        mockMvc
            .perform(get(URI_TEMPLATE, 999_999L))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("GAME_NOT_FOUND"));
    }

    @DisplayName("잘못된 status 값으로 조회하면 400을 받는다")
    @WithMockUser(roles = "ADMIN")
    @Test
    void should_return400_when_statusIsInvalid() throws Exception {
        mockMvc.perform(get(URI_TEMPLATE, gameId).param("status", "FOO")).andExpect(status().isBadRequest());
    }

    @DisplayName("미인증 사용자는 401을 받는다")
    @Test
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(get(URI_TEMPLATE, gameId)).andExpect(status().isUnauthorized());
    }
}
