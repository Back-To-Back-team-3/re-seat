package com.backtoback.reseat.domain.reservation.service;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
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
import com.backtoback.reseat.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * B4 버그(Redis–DB 정합성 불일치) 회귀 통합 테스트.
 *
 * <p>실제 H2 DB에 커밋된 상태를 검증한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class HoldExpiryConsistencyTest {

    @Autowired private HoldExpiryService holdExpiryService;

    @Autowired private ReservationRepository reservationRepository;
    @Autowired private GameSeatRepository gameSeatRepository;

    // 픽스처 구성에 필요한 연관 엔티티 저장소
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private StadiumRepository stadiumRepository;
    @Autowired private SeatZoneRepository seatZoneRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private GameRepository gameRepository;

    // @AfterEach 수동 정리용 ID 추적
    private Long savedReservationId;
    private Long savedGameSeatId;
    private Long savedUserId;
    private Long savedGameId;
    private Long savedSeatId;
    private Long savedZoneId;
    private Long savedStadiumId;
    private Long savedTeamId;

    /**
     * 각 테스트 전: Game, User, Seat, GameSeat, Reservation 픽스처를 순서대로 저장한다.
     * 상태와 hold_expires_at은 각 테스트에서 설정한다.
     */
    @BeforeEach
    void setUp() {
        // Stadium → SeatZone → Team 순서 (Team이 homeStadium 참조하므로 Stadium 먼저)
        Stadium stadium = stadiumRepository.save(Stadium.of("테스트구장", "서울시 테스트구", 100));
        savedStadiumId = stadium.getId();

        // SeatZone: grade·basePrice 필수
        SeatZone zone = seatZoneRepository.save(SeatZone.of(stadium, "1루존", SeatGrade.INFIELD, 18000));
        savedZoneId = zone.getId();

        // Team: homeStadium 필수
        Team team = teamRepository.save(Team.of("테스트팀", stadium));
        savedTeamId = team.getId();

        // Seat: stadium·zone·seatBlock·seatRow·seatNumber(String) 필수
        Seat seat = seatRepository.save(Seat.of(stadium, zone, "1", "A", "1"));
        savedSeatId = seat.getId();

        // User: @Builder — email·name 외 나머지는 기본값 적용
        User user = userRepository.save(User.builder()
            .email("b4-test@test.com")
            .name("B4테스터")
            .build());
        savedUserId = user.getId();

        // Game: @Builder — bookingStatus 미지정 시 SCHEDULED 기본값 적용
        Game game = gameRepository.save(Game.builder()
            .homeTeam(team)
            .awayTeam(team)
            .stadium(stadium)
            .gameAt(LocalDateTime.now().plusDays(1))
            .bookingOpenAt(LocalDateTime.now().minusHours(1))
            .bookingCloseAt(LocalDateTime.now().plusHours(5))
            .build());
        savedGameId = game.getId();

        // GameSeat: 상태·hold_expires_at은 각 테스트에서 hold()로 직접 세팅
        GameSeat gameSeat = gameSeatRepository.save(GameSeat.builder()
            .game(game)
            .seat(seat)
            .price(18000)
            .build());
        savedGameSeatId = gameSeat.getId();
    }

    /**
     * 각 테스트 후: reservation → game_seat → 연관 엔티티 순서로 수동 삭제한다.
     * FK 제약 위반 방지를 위해 자식 먼저 삭제한다.
     */
    @AfterEach
    void tearDown() {
        if (savedReservationId != null) {
            reservationRepository.deleteById(savedReservationId);
            savedReservationId = null;
        }
        if (savedGameSeatId != null) {
            gameSeatRepository.deleteById(savedGameSeatId);
            savedGameSeatId = null;
        }
        // FK 역순 삭제: game → team → seat → zone → user → stadium
        // Team이 stadium을 참조하므로 team을 stadium보다 먼저 삭제해야 한다
        if (savedGameId != null)    gameRepository.deleteById(savedGameId);
        if (savedTeamId != null)    teamRepository.deleteById(savedTeamId);
        if (savedSeatId != null)    seatRepository.deleteById(savedSeatId);
        if (savedZoneId != null)    seatZoneRepository.deleteById(savedZoneId);
        if (savedUserId != null)    userRepository.deleteById(savedUserId);
        if (savedStadiumId != null) stadiumRepository.deleteById(savedStadiumId);
    }

    // ──────────────────────────────────────────────────────────────────────
    // 시나리오 1: TTL 만료 후 game_seats HELD→AVAILABLE, reservations HOLDING→EXPIRED
    // ──────────────────────────────────────────────────────────────────────

    /**
     * B4 정합성 회귀 — TTL 만료 후 두 테이블이 동시에 올바른 상태로 전이되는지 검증한다.
     */
    @Test
    @DisplayName("TTL 만료 후 game_seats HELD→AVAILABLE, reservations HOLDING→EXPIRED 정합성 검증")
    void scenario1_ttlExpired_bothTablesTransitionCorrectly() {
        // given
        LocalDateTime expiredAt = LocalDateTime.now().minusMinutes(1); // 이미 만료된 시각

        // GameSeat: AVAILABLE → HELD 전이 (hold_expires_at 과거로 세팅)
        GameSeat gameSeat = gameSeatRepository.findById(savedGameSeatId).orElseThrow();
        gameSeat.hold(expiredAt);
        gameSeatRepository.save(gameSeat);

        // Reservation: HOLDING 상태, hold_expires_at 과거로 세팅
        Game game = gameRepository.findById(savedGameId).orElseThrow();
        User user = userRepository.findById(savedUserId).orElseThrow();
        Reservation reservation = reservationRepository.save(Reservation.builder()
            .reservationNo("RSV-TEST-000001")
            .user(user)
            .game(game)
            .status(ReservationStatus.HOLDING)
            .holdExpiresAt(expiredAt)
            .build());
        savedReservationId = reservation.getId();

        // when
        LocalDateTime now = LocalDateTime.now();
        HoldExpiryService.HoldExpiryResult result = holdExpiryService.releaseExpired(now);

        // then — 결과 카운트 검증
        assertThat(result.expiredReservations())
            .as("만료된 예약 1건이 EXPIRED로 전이되어야 한다")
            .isGreaterThanOrEqualTo(1);
        assertThat(result.releasedSeats())
            .as("만료된 좌석 1건이 AVAILABLE로 회수되어야 한다")
            .isGreaterThanOrEqualTo(1);

        // then — 실제 DB 상태 재조회 검증 (B4 핵심: 양쪽 테이블 모두 확인)
        Reservation updatedReservation = reservationRepository.findById(savedReservationId).orElseThrow();
        assertThat(updatedReservation.getStatus())
            .as("reservations.status가 EXPIRED로 전이되어야 한다")
            .isEqualTo(ReservationStatus.EXPIRED);

        GameSeat updatedGameSeat = gameSeatRepository.findById(savedGameSeatId).orElseThrow();
        assertThat(updatedGameSeat.getStatus())
            .as("game_seats.status가 AVAILABLE로 회수되어야 한다")
            .isEqualTo(GameSeatStatus.AVAILABLE);
        assertThat(updatedGameSeat.getHoldExpiresAt())
            .as("회수된 좌석의 hold_expires_at은 null이어야 한다")
            .isNull();
    }
}
