package com.backtoback.reseat.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.exception.LockFailedException;
import com.backtoback.reseat.domain.reservation.exception.SeatAlreadyHeldException;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
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

import lombok.extern.slf4j.Slf4j;

/**
 * [이슈 #382] 취소로 반환된 좌석에 대한 재선점 경합 검증.
 * <p>(userGameLockStrategy → 수량 검증 → seatLockStrategy → ReservationService.holdSeats)를
 * "처음부터 AVAILABLE인 좌석"이 아니라 "cancel()로 방금 AVAILABLE 전환된 좌석"에 대해 재현한다.</p>
 * <p>핵심 검증:
 * - 재선점 성공 정확히 1건, over-booking 0건
 * - 취소된 원 예약의 상태(CANCELED)가 경합 중 훼손되지 않음
 * </p>
 */
// @Disabled("테스트 제외")
@Slf4j
@EnabledIfEnvironmentVariable(
    named = "RUN_CONCURRENCY_TESTS",
    matches = "true"
)
@Tag("concurrency")
@ActiveProfiles("test-concurrency")
@SpringBootTest
class ReservationCancelThenHoldAgainConcurrencyTest {

    private static final int THREAD_COUNT = 10;
    private static final int AWAIT_SECONDS = 15;

    private final List<Long> competitorUserIds = new ArrayList<>();
    private final List<Long> competitorTokenIds = new ArrayList<>();

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private SeatHoldFacade seatHoldFacade;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private ReservationSeatRepository reservationSeatRepository;
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
    @Autowired
    private AdmissionTokenRepository admissionTokenRepository;

    private Long targetGameSeatId;
    private Long canceledReservationId;
    private Long originalHolderUserId;
    private Long gameId;
    private Long seatId;
    private Long seatZoneId;
    private Long stadiumId;
    private Long homeTeamId;
    private Long awayTeamId;

    @BeforeEach
    void setUp() {
        Stadium stadium = Stadium.of("테스트 구장", "서울시 테스트구 1", 10000);
        stadiumRepository.save(stadium);
        stadiumId = stadium.getId();

        Team homeTeam = Team.of("홈팀", stadium);
        Team awayTeam = Team.of("원정팀", stadium);
        teamRepository.save(homeTeam);
        teamRepository.save(awayTeam);
        homeTeamId = homeTeam.getId();
        awayTeamId = awayTeam.getId();

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
                .title("[이슈 #382] 취소 후 재선점 경합 테스트 경기")
                .build();
        gameRepository.save(game);
        gameId = game.getId();

        SeatZone zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, 18000);
        seatZoneRepository.save(zone);
        seatZoneId = zone.getId();

        Seat seat = Seat.of(stadium, zone, "A", "1", "1");
        seatRepository.save(seat);
        seatId = seat.getId();

        // 1. 원 홀더가 좌석을 선점(HELD)한 상태를 재현
        LocalDateTime holdExpiresAt = LocalDateTime.now().plusMinutes(10);
        GameSeat gameSeat
            = GameSeat.builder().game(game).seat(seat).price(18000).status(GameSeatStatus.AVAILABLE).build();
        gameSeatRepository.save(gameSeat);
        gameSeat.hold(holdExpiresAt);
        gameSeatRepository.save(gameSeat);
        targetGameSeatId = gameSeat.getId();

        User originalHolder
            = User
                .builder()
                .email("original-holder@reseat.com")
                .password("pw")
                .name("원홀더")
                .phone("010-3333-0000")
                .isVerified(true)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(originalHolder);
        originalHolderUserId = originalHolder.getId();

        Reservation reservation
            = Reservation
                .builder()
                .user(originalHolder)
                .game(game)
                .reservationNo("RSV-382-RACE-ORIGINAL")
                .status(ReservationStatus.HOLDING)
                .holdExpiresAt(holdExpiresAt)
                .build();
        ReservationSeat reservationSeat
            = ReservationSeat.builder().gameSeat(gameSeat).price(gameSeat.getPrice()).build();
        reservation.addReservationSeat(reservationSeat);
        reservationRepository.save(reservation);
        canceledReservationId = reservation.getId();

        // 2. 실제 cancel()을 호출해 좌석을 "취소로 갓 반환된" 상태로 만든다.
        reservationService.cancel(canceledReservationId);

        // 3. 서로 다른 사용자 THREAD_COUNT명 + 각자 유효한 Queue-Token 사전 발급
        LocalDateTime issuedAt = LocalDateTime.now();
        for (int i = 0; i < THREAD_COUNT; i++) {
            User competitor
                = User
                    .builder()
                    .email("hold-again-race-" + i + "@reseat.com")
                    .password("pw")
                    .name("경합유저" + i)
                    .phone("010-4444-" + String.format("%04d", i))
                    .isVerified(true)
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build();
            userRepository.save(competitor);
            competitorUserIds.add(competitor.getId());

            AdmissionToken token
                = AdmissionToken
                    .of(
                        game,
                        competitor,
                        "qt_hold-again-race-" + i,
                        issuedAt,
                        issuedAt.plusMinutes(21),
                        issuedAt.plusMinutes(3)
                    );
            admissionTokenRepository.save(token);
            competitorTokenIds.add(token.getId());
        }
    }

    @AfterEach
    void tearDown() {
        admissionTokenRepository.deleteAllById(competitorTokenIds);
        competitorTokenIds.clear();

        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        gameSeatRepository.deleteAll();

        if (gameId != null) {
            gameRepository.deleteById(gameId);
        }
        if (seatId != null) {
            seatRepository.deleteById(seatId);
        }
        if (seatZoneId != null) {
            seatZoneRepository.deleteById(seatZoneId);
        }
        if (homeTeamId != null) {
            teamRepository.deleteById(homeTeamId);
        }
        if (awayTeamId != null) {
            teamRepository.deleteById(awayTeamId);
        }
        if (stadiumId != null) {
            stadiumRepository.deleteById(stadiumId);
        }
        if (originalHolderUserId != null) {
            userRepository.deleteById(originalHolderUserId);
        }
        if (!competitorUserIds.isEmpty()) {
            userRepository.deleteAllById(competitorUserIds);
            competitorUserIds.clear();
        }
    }

    @Test
    @DisplayName("[이슈 #382] 취소로 반환된 좌석에 대한 동시 재선점 경합 시 성공 1건, over-booking 0건")
    void should_admitExactlyOneHolder_when_seatFreedByCancelThenHeldAgainConcurrently() throws InterruptedException {
        // given: setUp()에서 이미 cancel() 실행 완료 — targetGameSeatId는 현재 AVAILABLE
        GameSeat freedSeat = gameSeatRepository.findById(targetGameSeatId).orElseThrow();
        assertThat(freedSeat.getStatus()).as("전제 조건: 취소 직후 좌석은 AVAILABLE이어야 한다").isEqualTo(GameSeatStatus.AVAILABLE);

        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger seatAlreadyHeldCount = new AtomicInteger(0);
        AtomicInteger lockFailedCount = new AtomicInteger(0);
        AtomicInteger dataIntegrityViolationCount = new AtomicInteger(0);
        AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

        // when: 서로 다른 사용자 THREAD_COUNT명이 동시에 같은 좌석을 재선점 시도
        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long userId = competitorUserIds.get(i);
            final String token = "qt_hold-again-race-" + i;

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    seatHoldFacade.holdSeats(userId, token, new SeatHoldRequest(gameId, List.of(targetGameSeatId)));
                    successCount.incrementAndGet();
                } catch (SeatAlreadyHeldException e) {
                    seatAlreadyHeldCount.incrementAndGet();
                } catch (LockFailedException e) {
                    lockFailedCount.incrementAndGet();
                } catch (DataIntegrityViolationException e) {
                    log.error("[이슈 #382] DataIntegrityViolation — 락 설계 점검 필요: {}", e.getMessage());
                    dataIntegrityViolationCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("[이슈 #382] 예상 외 예외 발생: {}", e.getClass().getSimpleName(), e);
                    unexpectedExceptionCount.incrementAndGet();
                }
            });
        }

        boolean finished = false;
        try {
            readyLatch.await();
            startLatch.countDown();
            executor.shutdown();
            finished = executor.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            startLatch.countDown();
            executor.shutdownNow();
        }

        // then
        GameSeat finalGameSeat = gameSeatRepository.findById(targetGameSeatId).orElseThrow();
        Reservation originalReservation = reservationRepository.findById(canceledReservationId).orElseThrow();

        // cancel()이 행을 삭제하지 않으므로, HOLDING 상태 행만 필터링해서 세야 한다.
        long holdingReservationSeatRows
            = reservationSeatRepository
                .findAll()
                .stream()
                .filter(rs -> rs.getGameSeat().getId().equals(targetGameSeatId))
                .filter(rs -> !rs.getReservation().getId().equals(canceledReservationId))
                .count();

        log.info("===================================================");
        log.info("[이슈 #382] 취소 후 재선점 경합 검증 수치");
        log.info("  동시 스레드 수                : {}", THREAD_COUNT);
        log.info("  재선점 성공 건수              : {}", successCount.get());
        log.info("  SEAT_ALREADY_HELD 건수        : {}", seatAlreadyHeldCount.get());
        log.info("  LOCK_FAILED 건수              : {}", lockFailedCount.get());
        log.info("  DataIntegrityViolation 건수   : {}", dataIntegrityViolationCount.get());
        log.info("  예상 외 예외 건수              : {}", unexpectedExceptionCount.get());
        log.info("  game_seats.status             : {}", finalGameSeat.getStatus());
        log.info("  HOLDING reservation_seats 행 수 : {}", holdingReservationSeatRows);
        log.info("  원 예약(취소) 상태             : {}", originalReservation.getStatus());
        log.info("===================================================");

        assertThat(finished).as("%d초 내에 모든 스레드가 종료되지 않았다 — 데드락 또는 타임아웃 의심", AWAIT_SECONDS).isTrue();

        assertThat(successCount.get()).as("재선점 성공은 정확히 1건이어야 한다").isEqualTo(1);
        assertThat(holdingReservationSeatRows)
            .as("HOLDING 상태의 reservation_seats 행은 1건이어야 한다 — over-booking 0건")
            .isEqualTo(1);
        assertThat(finalGameSeat.getStatus()).as("최종 좌석 상태는 HELD여야 한다").isEqualTo(GameSeatStatus.HELD);

        assertThat(dataIntegrityViolationCount.get()).as("DB 유니크 위반은 0건이어야 한다").isZero();
        assertThat(unexpectedExceptionCount.get()).as("예상 외 예외는 0건이어야 한다").isZero();

        assertThat(seatAlreadyHeldCount.get() + lockFailedCount.get())
            .as("실패 건수 합계는 THREAD_COUNT - 1이어야 한다")
            .isEqualTo(THREAD_COUNT - 1);

        // 경합 중 원래 취소된 예약이 훼손되지 않았는지 확인 (좌석 상태 불일치 0건의 일부)
        assertThat(originalReservation.getStatus())
            .as("취소된 원 예약 상태는 경합 후에도 CANCELED로 유지되어야 한다")
            .isEqualTo(ReservationStatus.CANCELED);
    }
}
