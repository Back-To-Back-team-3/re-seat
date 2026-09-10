package com.backtoback.reseat.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.repository.ReservationRepository;
import com.backtoback.reseat.domain.reservation.repository.ReservationSeatRepository;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeatStatus;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;
import com.backtoback.reseat.domain.seatinventory.service.GameSeatStatusService;
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

import jakarta.persistence.PessimisticLockException;
import lombok.extern.slf4j.Slf4j;

/**
 * [이슈 #382] 취소(cancel) 동시 요청 좌석 반환 단일성 검증.
 * <p> ReservationService.cancel()은 findByIdWithPessimisticWriteLock으로 예약 행을 잠그고,
 * 이미 취소된 예약(isCanceled()==true)에 대해서는 예외 없이 조용히 리턴한다.
 * 반환값·예외만으로는 "실제 반환 스레드"와 "멱등 스킵 스레드"를 구분할 수 없으므로,
 * GameSeatStatusService.releaseSeat() 호출 횟수를 이용해 직접 카운트한다.
 * <p> 핵심 검증:
 * - releaseSeat() 호출 정확히 1회 (좌석 이중 반환 0건)
 * - GameSeat.release() 가드 위반(InvalidStateTransitionException) 0건
 * - 최종 game_seats.status == AVAILABLE, reservations.status == CANCELED
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
class ReservationCancelConcurrencyTest {

    private static final int THREAD_COUNT = 30;
    private static final int AWAIT_SECONDS = 30;

    @Autowired
    private ReservationService reservationService;
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

    // ReservationService가 실제로 의존 주입받는 빈을 감싼다.
    // cancel() 내부의 releaseSeat() 호출 횟수를 직접 관찰한다.
    @MockitoSpyBean
    private GameSeatStatusService gameSeatStatusService;

    private Long targetReservationId;
    private Long targetGameSeatId;
    private Long gameId;
    private Long seatId;
    private Long seatZoneId;
    private Long stadiumId;
    private Long homeTeamId;
    private Long awayTeamId;
    private Long userId;

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

        Game game = Game
            .builder()
            .homeTeam(homeTeam)
            .awayTeam(awayTeam)
            .stadium(stadium)
            .gameAt(LocalDateTime.now().plusDays(7))
            .bookingOpenAt(LocalDateTime.now().minusHours(1))
            .bookingCloseAt(LocalDateTime.now().plusDays(6))
            .bookingStatus(BookingStatus.OPEN)
            .title("[이슈 #382] 취소 동시성 테스트 경기")
            .build();
        gameRepository.save(game);
        gameId = game.getId();

        SeatZone zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, 18000);
        seatZoneRepository.save(zone);
        seatZoneId = zone.getId();

        Seat seat = Seat.of(stadium, zone, "A", "1", "1");
        seatRepository.save(seat);
        seatId = seat.getId();

        // 이미 선점 완료(HELD)된 좌석 — cancel()의 전제 조건
        LocalDateTime holdExpiresAt = LocalDateTime.now().plusMinutes(10);
        GameSeat gameSeat = GameSeat
            .builder()
            .game(game)
            .seat(seat)
            .price(18000)
            .status(GameSeatStatus.AVAILABLE)
            .build();
        gameSeatRepository.save(gameSeat);
        gameSeat.hold(holdExpiresAt);
        gameSeatRepository.save(gameSeat);
        targetGameSeatId = gameSeat.getId();

        User user = User
            .builder()
            .email("cancel-concurrency@reseat.com")
            .password("pw")
            .name("취소동시성테스트유저")
            .phone("010-2222-0001")
            .isVerified(true)
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.save(user);
        userId = user.getId();

        // HOLDING 상태 예약 1건 + 좌석 1건 연결
        Reservation reservation = Reservation
            .builder()
            .user(user)
            .game(game)
            .reservationNo("RSV-382-CONCURRENCY-TEST")
            .status(ReservationStatus.HOLDING)
            .holdExpiresAt(holdExpiresAt)
            .build();
        ReservationSeat reservationSeat = ReservationSeat
            .builder()
            .gameSeat(gameSeat)
            .price(gameSeat.getPrice())
            .build();
        reservation.addReservationSeat(reservationSeat);
        reservationRepository.save(reservation);
        targetReservationId = reservation.getId();

        clearInvocations(gameSeatStatusService);
    }

    @AfterEach
    void tearDown() {
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
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    @DisplayName("[이슈 #382] 동일 예약 cancel() 동시 호출 시 좌석 반환은 정확히 1회만 실행된다")
    void should_releaseSeatExactlyOnce_when_cancelRequestedConcurrently() throws InterruptedException {
        // given
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        AtomicInteger normalReturnCount = new AtomicInteger(0);
        AtomicInteger lockTimeoutCount = new AtomicInteger(0);
        AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

        // when: THREAD_COUNT개의 스레드가 동일 reservationId에 대해 cancel()을 동시 호출
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    reservationService.cancel(targetReservationId);
                    normalReturnCount.incrementAndGet();
                } catch (PessimisticLockException | org.springframework.dao.PessimisticLockingFailureException e) {
                    // 락 대기 2초 초과 — 스레드 수 과다로 인한 잡음 가능성. 완료 기준 위반과는 별도로 집계한다.
                    lockTimeoutCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("[이슈 #382] 예상 외 예외 발생: {}", e.getClass().getSimpleName(), e);
                    unexpectedExceptionCount.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS);

        // then
        Reservation finalReservation = reservationRepository.findById(targetReservationId).orElseThrow();
        GameSeat finalGameSeat = gameSeatRepository.findById(targetGameSeatId).orElseThrow();

        log.info("=====================================================");
        log.info("[이슈 #382] 취소 동시 요청 좌석 반환 단일성 검증 수치");
        log.info("  동시 스레드 수                 : {}", THREAD_COUNT);
        log.info("  정상 리턴(멱등 포함) 건수       : {}", normalReturnCount.get());
        log.info("  락 타임아웃 건수                : {}", lockTimeoutCount.get());
        log.info("  예상 외 예외 건수               : {}", unexpectedExceptionCount.get());
        log.info("  reservations.status             : {}", finalReservation.getStatus());
        log.info("  game_seats.status                : {}", finalGameSeat.getStatus());
        log.info("=====================================================");

        assertThat(finished).as("%d초 내에 모든 스레드가 종료되지 않았다 — 데드락 의심", AWAIT_SECONDS).isTrue();

        // 완료 기준 핵심: 이중 반환 방어 가드(InvalidStateTransitionException) 위반이 없었어야 한다
        assertThat(unexpectedExceptionCount.get()).as("예상 외 예외(가드 위반 포함)는 0건이어야 한다").isZero();

        // releaseSeat()는 정확히 1회만 실제 호출되어야 한다 (나머지는 isCanceled() 조기 리턴)
        verify(gameSeatStatusService, times(1)).releaseSeat(targetGameSeatId);

        // 최종 상태 정합성
        assertThat(finalReservation.getStatus()).as("예약은 CANCELED로 확정되어야 한다").isEqualTo(ReservationStatus.CANCELED);
        assertThat(finalGameSeat.getStatus()).as("좌석은 AVAILABLE로 정확히 1회 복귀해야 한다").isEqualTo(GameSeatStatus.AVAILABLE);
        assertThat(finalGameSeat.getHoldExpiresAt()).as("반환된 좌석의 holdExpiresAt은 초기화되어야 한다").isNull();

        assertThat(normalReturnCount.get() + lockTimeoutCount.get())
            .as("정상 리턴 + 락 타임아웃 합계는 THREAD_COUNT와 같아야 한다")
            .isEqualTo(THREAD_COUNT);
    }
}
