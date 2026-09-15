package com.backtoback.reseat.domain.reservation.service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.service.AdmissionTokenService;
import com.backtoback.reseat.domain.queue.service.AdmissionTokenTiming;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationResponse;
import com.backtoback.reseat.domain.reservation.service.ReservationService;
import com.backtoback.reseat.domain.reservation.service.SeatHoldFacade;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
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
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.common.BaseIntegrationTest;

/**
 * [이슈 #491] 취소 후 동일 좌석 재선점 회귀 테스트.
 * <p>같은 물리 좌석을 취소 → 재선점을 여러 차례 반복해도
 * reservation_seats.game_seat_id UNIQUE 위반(DataIntegrityViolationException)이
 * 발생하지 않음을 검증한다.
 * <p> reservation_id는 재선점마다 새로 발급되므로
 * uk_reservation_seats_game_seat_reservation(game_seat_id, reservation_id 복합 UNIQUE, V38)이
 * 위반되지 않아야 한다는 것이 이 테스트의 핵심 검증 대상이다.
 */
class CancelThenHoldSameSeatAgainIntegrationTest extends BaseIntegrationTest {

    private static final int PRICE = 18_000;
    private static final String TOKEN = "qt_cancel-hold-same-seat-test";
    private static final int REPEAT_COUNT = 3; // 이력이 여러 건 쌓여도 계속 성공해야 함을 증명

    @MockitoBean
    private AdmissionTokenService admissionTokenService;

    @Autowired
    private SeatHoldFacade seatHoldFacade;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private GameSeatRepository gameSeatRepository;
    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private SeatZoneRepository seatZoneRepository;
    @Autowired
    private StadiumRepository stadiumRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private UserRepository userRepository;

    private Long userId;
    private Long gameId;
    private Long gameSeatId;

    @BeforeEach
    void setUp() {
        doNothing().when(admissionTokenService).validateToken(anyLong(), anyLong(), anyString());
        doNothing().when(admissionTokenService).completeSeatBrowsing(anyLong(), anyLong(), anyString());
        when(admissionTokenService.getTokenTiming(anyLong(), anyLong(), anyString()))
            .thenReturn(new AdmissionTokenTiming(LocalDateTime.now().plusMinutes(21), null));

        Stadium stadium = Stadium.of("테스트 구장", "테스트시 테스트구", 10_000);
        stadiumRepository.save(stadium);

        Team homeTeam = Team.of("홈팀", stadium);
        Team awayTeam = Team.of("원정팀", stadium);
        teamRepository.save(homeTeam);
        teamRepository.save(awayTeam);

        Game game
            = Game
                .builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium(stadium)
                .gameAt(LocalDateTime.now().plusDays(7))
                .bookingOpenAt(LocalDateTime.now().minusHours(1))
                .bookingCloseAt(LocalDateTime.now().plusDays(6))
                .bookingStatus(BookingStatus.OPEN)
                .title("[이슈 #491] 동일 좌석 반복 재선점 검증 경기")
                .build();
        gameRepository.save(game);
        gameId = game.getId();

        SeatZone zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, PRICE);
        seatZoneRepository.save(zone);

        Seat seat = Seat.of(stadium, zone, "A", "1", "1");
        seatRepository.save(seat);
        GameSeat gameSeat
            = GameSeat.builder().game(game).seat(seat).price(PRICE).status(GameSeatStatus.AVAILABLE).build();
        gameSeatRepository.save(gameSeat);
        gameSeatId = gameSeat.getId();

        User user
            = User
                .builder()
                .email("cancel-hold-same-seat@test.com")
                .password("pw")
                .name("동일좌석재선점테스트")
                .phone("010-9999-0000")
                .isVerified(true)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);
        userId = user.getId();
    }

    @Test
    @DisplayName("should_holdSameSeatRepeatedly_when_reservationCanceledEachTime")
    void should_holdSameSeatRepeatedly_when_reservationCanceledEachTime() {
        for (int i = 0; i < REPEAT_COUNT; i++) {
            // when: 취소로 AVAILABLE 상태가 된 "동일" 좌석을 다시 선점한다.
            // reservation_id는 재선점마다 새로 발급되므로 uk_reservation_seats_game_seat_reservation
            // (game_seat_id, reservation_id 복합 UNIQUE, V38)이 위반되지 않는다.
            ReservationResponse hold
                = seatHoldFacade.holdSeats(userId, TOKEN, new SeatHoldRequest(gameId, List.of(gameSeatId)));

            // then: UNIQUE 위반 없이 매번 성공하고, 좌석은 HELD로 전이된다.
            assertThat(hold.reservationId()).isNotNull();
            assertThat(gameSeatRepository.findById(gameSeatId).orElseThrow().getStatus())
                .isEqualTo(GameSeatStatus.HELD);

            // 다음 반복을 위해 다시 취소해 AVAILABLE로 되돌린다.
            reservationService.cancel(hold.reservationId());
            assertThat(gameSeatRepository.findById(gameSeatId).orElseThrow().getStatus())
                .isEqualTo(GameSeatStatus.AVAILABLE);
        }
    }
}
