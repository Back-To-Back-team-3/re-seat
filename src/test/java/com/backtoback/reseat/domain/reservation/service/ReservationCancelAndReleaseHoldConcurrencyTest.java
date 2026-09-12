package com.backtoback.reseat.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
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

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationSeat;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.reservation.exception.InvalidReservationStatusException;
import com.backtoback.reseat.domain.reservation.exception.PreReservationExpiredException;
import com.backtoback.reseat.domain.reservation.exception.ReservationAccessDeniedException;
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
 * [이슈 #382] cancel()·releaseHold() 동시 호출 좌석 이중 반환 방지 검증.
 * <p>두 메서드 모두 findByIdWithPessimisticWriteLock으로 같은 예약 행을 잠그므로,
 * 정상 동작한다면 먼저 락을 잡은 쪽만 도메인 Reservation.cancel()까지 도달하고
 * 나머지 쪽은 커밋된 최신 상태(isCanceled()==true)를 보고 조용히 리턴해야 한다.</p>
 * <p>Reservation.cancel()은 requireHolding() 가드를 갖고 있어, 만약 서비스 레벨의
 * isCanceled() 체크를 두 스레드가 동시에 통과하는 회귀가 발생하면
 * 두 번째 도메인 cancel() 호출에서 InvalidReservationStatusException이 던져진다.
 * 이 예외가 0건인지가 이번 테스트의 핵심 판별 기준이다.</p>
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
class ReservationCancelAndReleaseHoldConcurrencyTest {

    private static final int AWAIT_SECONDS = 15;

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

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
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
                .title("[이슈 #382] cancel·releaseHold 동시성 테스트 경기")
                .build();
        gameRepository.save(game);
        gameId = game.getId();

        SeatZone zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, 18000);
        seatZoneRepository.save(zone);
        seatZoneId = zone.getId();

        Seat seat = Seat.of(stadium, zone, "A", "1", "1");
        seatRepository.save(seat);
        seatId = seat.getId();

        // 만료와 무관함을 보장하기 위해 넉넉히 미래로 설정 — PreReservationExpiredException(410) 배제
        LocalDateTime holdExpiresAt = LocalDateTime.now().plusMinutes(10);
        GameSeat gameSeat
            = GameSeat.builder().game(game).seat(seat).price(18000).status(GameSeatStatus.AVAILABLE).build();
        gameSeatRepository.save(gameSeat);
        gameSeat.hold(holdExpiresAt);
        gameSeatRepository.save(gameSeat);
        targetGameSeatId = gameSeat.getId();

        User user
            = User
                .builder()
                .email("cancel-releasehold-concurrency@reseat.com")
                .password("pw")
                .name("취소해제동시성테스트유저")
                .phone("010-5555-0000")
                .isVerified(true)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);
        userId = user.getId();

        Reservation reservation
            = Reservation
                .builder()
                .user(user)
                .game(game)
                .reservationNo("RSV-382-CANCEL-RELEASEHOLD")
                .status(ReservationStatus.HOLDING)
                .holdExpiresAt(holdExpiresAt)
                .build();
        ReservationSeat reservationSeat
            = ReservationSeat.builder().gameSeat(gameSeat).price(gameSeat.getPrice()).build();
        reservation.addReservationSeat(reservationSeat);
        reservationRepository.save(reservation);
        targetReservationId = reservation.getId();

        org.mockito.Mockito.clearInvocations(gameSeatStatusService);
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
    @DisplayName(
        "[이슈 #382] 동일 예약에 cancel()·releaseHold() 동시 호출 시 좌석 이중 반환 0건, " + "InvalidReservationStatusException 0건"
    )
    void should_releaseSeatOnlyOnce_when_cancelAndReleaseHoldCalledConcurrently() throws InterruptedException {
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger invalidStatusCount = new AtomicInteger(0);
        AtomicInteger accessDeniedCount = new AtomicInteger(0);
        AtomicInteger expiredCount = new AtomicInteger(0);
        AtomicInteger lockTimeoutCount = new AtomicInteger(0);
        AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

        Runnable cancelTask = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                reservationService.cancel(targetReservationId);
            } catch (InvalidReservationStatusException e) {
                invalidStatusCount.incrementAndGet();
            } catch (PessimisticLockException | org.springframework.dao.PessimisticLockingFailureException e) {
                lockTimeoutCount.incrementAndGet();
            } catch (Exception e) {
                log.error("[이슈 #382] cancel() 예상 외 예외: {}", e.getClass().getSimpleName(), e);
                unexpectedExceptionCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        Runnable releaseHoldTask = () -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                reservationService.releaseHold(targetReservationId, userId);
            } catch (InvalidReservationStatusException e) {
                invalidStatusCount.incrementAndGet();
            } catch (ReservationAccessDeniedException e) {
                // 동일 사용자로 호출하므로 이 경로는 발생하지 않아야 한다.
                accessDeniedCount.incrementAndGet();
            } catch (PreReservationExpiredException e) {
                // 만료 전 예약이므로 이 경로는 발생하지 않아야 한다.
                expiredCount.incrementAndGet();
            } catch (PessimisticLockException | org.springframework.dao.PessimisticLockingFailureException e) {
                lockTimeoutCount.incrementAndGet();
            } catch (Exception e) {
                log.error("[이슈 #382] releaseHold() 예상 외 예외: {}", e.getClass().getSimpleName(), e);
                unexpectedExceptionCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        new Thread(cancelTask).start();
        new Thread(releaseHoldTask).start();
        readyLatch.await();
        startLatch.countDown();
        boolean finished = doneLatch.await(AWAIT_SECONDS, TimeUnit.SECONDS);

        Reservation finalReservation = reservationRepository.findById(targetReservationId).orElseThrow();
        GameSeat finalGameSeat = gameSeatRepository.findById(targetGameSeatId).orElseThrow();

        log.info("======================================================");
        log.info("[이슈 #382] cancel·releaseHold 동시 호출 검증 수치");
        log.info("  InvalidReservationStatusException 건수 : {}", invalidStatusCount.get());
        log.info("  ReservationAccessDenied 건수            : {}", accessDeniedCount.get());
        log.info("  PreReservationExpired 건수              : {}", expiredCount.get());
        log.info("  락 타임아웃 건수                        : {}", lockTimeoutCount.get());
        log.info("  예상 외 예외 건수                       : {}", unexpectedExceptionCount.get());
        log.info("  reservations.status                     : {}", finalReservation.getStatus());
        log.info("  game_seats.status                       : {}", finalGameSeat.getStatus());
        log.info("======================================================");

        assertThat(finished).as("%d초 내에 두 스레드가 종료되지 않았다 — 데드락 의심", AWAIT_SECONDS).isTrue();

        // 핵심 판별: 도메인 가드가 뚫려 이중 취소가 시도된 흔적이 있으면 안 된다.
        assertThat(invalidStatusCount.get())
            .as("InvalidReservationStatusException은 0건이어야 한다 — 좌석 이중 반환 방지 실패")
            .isZero();
        assertThat(accessDeniedCount.get()).as("동일 사용자 호출이므로 소유권 거부는 0건이어야 한다").isZero();
        assertThat(expiredCount.get()).as("만료 전 예약이므로 만료 예외는 0건이어야 한다").isZero();
        assertThat(unexpectedExceptionCount.get()).as("예상 외 예외는 0건이어야 한다").isZero();

        // releaseSeat()는 정확히 1회만 실제 호출되어야 한다.
        org.mockito.Mockito.verify(gameSeatStatusService, org.mockito.Mockito.times(1)).releaseSeat(targetGameSeatId);

        assertThat(finalReservation.getStatus()).as("예약은 CANCELED로 확정되어야 한다").isEqualTo(ReservationStatus.CANCELED);
        assertThat(finalGameSeat.getStatus()).as("좌석은 AVAILABLE로 정확히 1회 복귀해야 한다").isEqualTo(GameSeatStatus.AVAILABLE);
    }
}
