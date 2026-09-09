package com.backtoback.reseat.domain.reservation.service;

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
 * [이슈 #380]
 * HOLDING 상태 예약 2매 중 1매를 취소하면 잔여 수량 기준으로 다른 좌석을 재선점할 수 있음을 검증한다.
 * <p>ReservationService.cancel() 한 번으로 예약 취소와 좌석 반환(AVAILABLE)이 함께 이뤄지는지 재선점 성공 여부로 확인한다.
 */
class CancelThenHoldAgainIntegrationTest extends BaseIntegrationTest {

    private static final int PRICE = 18_000;
    private static final String TOKEN = "qt_cancel-hold-again-test";

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
    private Long firstGameSeatId;
    private Long secondGameSeatId;
    private Long thirdGameSeatId; // 재선점 대상 — first와 다른 물리 좌석

    @BeforeEach
    void setUp() {
        // Queue-Token 검증은 항상 통과, 재선점 상한도 걸리지 않도록 넉넉한 타이밍을 반환한다.
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
                .title("[이슈 #380] 취소 후 재선점 검증 경기")
                .build();
        gameRepository.save(game);
        gameId = game.getId();

        SeatZone zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, PRICE);
        seatZoneRepository.save(zone);

        firstGameSeatId = createAvailableSeat(game, zone, "1");
        secondGameSeatId = createAvailableSeat(game, zone, "2");
        thirdGameSeatId = createAvailableSeat(game, zone, "3");

        User user
            = User
                .builder()
                .email("cancel-hold-again@test.com")
                .password("pw")
                .name("취소재선점테스트")
                .phone("010-7777-8888")
                .isVerified(true)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);
        userId = user.getId();
    }

    @Test
    @DisplayName("should_holdAnotherSeat_when_oneOfTwoHoldingSeatsIsCanceled")
    void should_holdAnotherSeat_when_oneOfTwoHoldingSeatsIsCanceled() {

        // given: 2매(첫 번째·두 번째 좌석) HOLDING 상태로 선점한다.
        ReservationResponse firstHold
            = seatHoldFacade
                .holdSeats(userId, TOKEN, new SeatHoldRequest(gameId, List.of(firstGameSeatId, secondGameSeatId)));

        // cancel() 한 번으로 예약 취소 + 좌석 반환(AVAILABLE)까지 이어져야 한다(PR #444 반영).
        reservationService.cancel(firstHold.reservationId());

        // when: 잔여 수량(0매 보유) 범위 안에서 세 번째(다른) 좌석을 재선점한다.
        ReservationResponse newHoldResponse
            = seatHoldFacade.holdSeats(userId, TOKEN, new SeatHoldRequest(gameId, List.of(thirdGameSeatId)));

        // then: 재선점이 예외 없이 성공하고, 취소한 두 좌석 모두 AVAILABLE로 되돌아가 있어야 한다.
        assertThat(newHoldResponse.reservationId()).isNotNull();
        assertThat(gameSeatRepository.findById(thirdGameSeatId).orElseThrow().getStatus())
            .isEqualTo(GameSeatStatus.HELD);
        assertThat(gameSeatRepository.findById(firstGameSeatId).orElseThrow().getStatus())
            .isEqualTo(GameSeatStatus.AVAILABLE);
        assertThat(gameSeatRepository.findById(secondGameSeatId).orElseThrow().getStatus())
            .isEqualTo(GameSeatStatus.AVAILABLE);
    }

    private Long createAvailableSeat(Game game, SeatZone zone, String number) {
        Seat seat = Seat.of(game.getStadium(), zone, "A", "1", number);
        seatRepository.save(seat);
        GameSeat gameSeat
            = GameSeat.builder().game(game).seat(seat).price(PRICE).status(GameSeatStatus.AVAILABLE).build();
        gameSeatRepository.save(gameSeat);
        return gameSeat.getId();
    }
}
