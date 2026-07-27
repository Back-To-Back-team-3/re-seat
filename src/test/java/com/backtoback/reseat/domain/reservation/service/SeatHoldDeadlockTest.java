package com.backtoback.reseat.domain.reservation.service;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [이슈 #174] 교차 순서 다좌석 요청 데드락 방지 회귀 테스트.
 *
 * <p>이 테스트는 교차 순서 요청에서 상호 대기 없이 처리 완료됨을 검증한다.</p>
 *
 * <p>핵심 검증:
 * <ul>
 *   <li>15초 내 모든 스레드 종료 — 데드락 없음 증명</li>
 *   <li>예상 외 예외 0건</li>
 *   <li>LOCK_FAILED는 경합으로 발생 가능하며 정상 동작</li>
 * </ul>
 * </p>
 */
@Disabled("테스트 제외")
@Slf4j
@EnabledIfEnvironmentVariable(
    named = "RUN_CONCURRENCY_TESTS",
    matches = "true"
)
@Tag("concurrency")
@ActiveProfiles("test-concurrency")
@SpringBootTest
class SeatHoldDeadlockTest {

    @Autowired private SeatHoldFacade seatHoldFacade;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationSeatRepository reservationSeatRepository;
    @Autowired private GameSeatRepository gameSeatRepository;
    @Autowired private GameRepository gameRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private SeatZoneRepository seatZoneRepository;
    @Autowired private StadiumRepository stadiumRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AdmissionTokenRepository admissionTokenRepository;

    private Long gameId;
    private Long seat1Id;
    private Long seat2Id;
    private Long seatZoneId;
    private Long stadiumId;
    private Long homeTeamId;
    private Long awayTeamId;
    private Long userId1;
    private Long userId2;
    private Long tokenId1;
    private Long tokenId2;

    // @Transactional 금지 → @AfterEach 수동 정리
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

        Game game = Game.builder()
            .homeTeam(homeTeam)
            .awayTeam(awayTeam)
            .stadium(stadium)
            .gameAt(LocalDateTime.now().plusDays(7))
            .bookingOpenAt(LocalDateTime.now().minusHours(1))
            .bookingCloseAt(LocalDateTime.now().plusDays(6))
            .bookingStatus(BookingStatus.OPEN)
            .title("[이슈 #174] 데드락 방지 테스트 경기")
            .build();
        gameRepository.save(game);
        gameId = game.getId();

        SeatZone zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, 18000);
        seatZoneRepository.save(zone);
        seatZoneId = zone.getId();

        // 다좌석 테스트용 좌석 2개
        Seat seat1 = Seat.of(stadium, zone, "A", "1", "1");
        Seat seat2 = Seat.of(stadium, zone, "A", "1", "2");
        seatRepository.save(seat1);
        seatRepository.save(seat2);

        GameSeat gameSeat1 = GameSeat.builder()
            .game(game).seat(seat1).price(18000).status(GameSeatStatus.AVAILABLE).build();
        GameSeat gameSeat2 = GameSeat.builder()
            .game(game).seat(seat2).price(18000).status(GameSeatStatus.AVAILABLE).build();
        gameSeatRepository.save(gameSeat1);
        gameSeatRepository.save(gameSeat2);
        seat1Id = gameSeat1.getId();
        seat2Id = gameSeat2.getId();

        // 스레드1 사용자
        User user1 = User.builder()
            .email("deadlock-test-1@reseat.com").password("pw").name("테스트유저1")
            .phone("010-2222-0001").isVerified(true).role(UserRole.USER).status(UserStatus.ACTIVE)
            .build();
        userRepository.save(user1);
        userId1 = user1.getId();

        AdmissionToken token1 = AdmissionToken.of(
            game, user1, "qt_deadlock-token-1",
            LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));
        admissionTokenRepository.save(token1);
        tokenId1 = token1.getId();

        // 스레드2 사용자
        User user2 = User.builder()
            .email("deadlock-test-2@reseat.com").password("pw").name("테스트유저2")
            .phone("010-2222-0002").isVerified(true).role(UserRole.USER).status(UserStatus.ACTIVE)
            .build();
        userRepository.save(user2);
        userId2 = user2.getId();

        AdmissionToken token2 = AdmissionToken.of(
            game, user2, "qt_deadlock-token-2",
            LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));
        admissionTokenRepository.save(token2);
        tokenId2 = token2.getId();
    }

    @AfterEach
    void tearDown() {
        admissionTokenRepository.deleteAllById(List.of(tokenId1, tokenId2));

        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        gameSeatRepository.deleteAll();

        if (gameId != null) gameRepository.deleteById(gameId);
        seatRepository.deleteAll();
        if (seatZoneId != null) seatZoneRepository.deleteById(seatZoneId);
        if (homeTeamId != null) teamRepository.deleteById(homeTeamId);
        if (awayTeamId != null) teamRepository.deleteById(awayTeamId);
        if (stadiumId != null) stadiumRepository.deleteById(stadiumId);

        userRepository.deleteAllById(List.of(userId1, userId2));
    }

    @Test
    @DisplayName("[B3 데드락 방지] 교차 순서 다좌석 요청에서 상호 대기 없이 15초 내 처리 완료")
    void should_completeWithoutDeadlock_when_crossOrderRequestsCompete() throws InterruptedException {
        // given
        // 스레드1: [seat1, seat2] — 오름차순
        // 스레드2: [seat2, seat1] — 역순 (정렬 없으면 데드락 조건)
        // 정렬 적용 후 두 스레드 모두 seat1 → seat2 순으로 획득 → 순환 대기 불성립
        SeatHoldRequest request1 = new SeatHoldRequest(gameId, List.of(seat1Id, seat2Id));
        SeatHoldRequest request2 = new SeatHoldRequest(gameId, List.of(seat2Id, seat1Id));

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger lockFailedCount = new AtomicInteger(0);
        AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // when
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                seatHoldFacade.holdSeats(userId1, "qt_deadlock-token-1", request1);
                successCount.incrementAndGet();
            } catch (com.backtoback.reseat.domain.reservation.exception.SeatAlreadyHeldException e) {
            // 락 획득 후 좌석 재검증에서 실패 — 동일 좌석 경합 시 정상 동작
                lockFailedCount.incrementAndGet();
            } catch (com.backtoback.reseat.domain.reservation.exception.LockFailedException e) {
                // 경합으로 인한 LOCK_FAILED — 데드락이 아닌 정상 실패
                lockFailedCount.incrementAndGet();
            } catch (Exception e) {
                log.error("[B3] 스레드1 예상 외 예외: {}", e.getClass().getSimpleName(), e);
                unexpectedExceptionCount.incrementAndGet();
            }
        });

        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                seatHoldFacade.holdSeats(userId2, "qt_deadlock-token-2", request2);
                successCount.incrementAndGet();
            } catch (com.backtoback.reseat.domain.reservation.exception.SeatAlreadyHeldException e) {
                // 락 획득 후 좌석 재검증에서 실패 — 동일 좌석 경합 시 정상 동작
                lockFailedCount.incrementAndGet();
            } catch (com.backtoback.reseat.domain.reservation.exception.LockFailedException e) {
                lockFailedCount.incrementAndGet();
            } catch (Exception e) {
                log.error("[B3] 스레드2 예상 외 예외: {}", e.getClass().getSimpleName(), e);
                unexpectedExceptionCount.incrementAndGet();
            }
        });

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);

        // then
        log.info("════════════════════════════════════════════════════");
        log.info("[이슈 #174] 교차 순서 다좌석 데드락 방지 검증 수치");
        log.info("  15초 내 종료 여부   : {}", finished);
        log.info("  선점 성공 건수      : {}", successCount.get());
        log.info("  LOCK_FAILED 건수   : {}", lockFailedCount.get());
        log.info("  예상 외 예외 건수   : {}", unexpectedExceptionCount.get());
        log.info("  ※ LOCK_FAILED는 경합으로 발생 가능 — 데드락과 다름");
        log.info("════════════════════════════════════════════════════");

        // 핵심: 15초 내 종료 = 데드락 없음 증명
        assertThat(finished)
            .as("15초 내 모든 스레드가 종료되지 않았다 — 데드락 의심")
            .isTrue();

        assertThat(unexpectedExceptionCount.get())
            .as("예상 외 예외는 0건이어야 한다")
            .isZero();

        // 두 스레드가 서로 다른 좌석 2개를 각각 요청하므로 둘 다 성공 가능
        // 단, 경합으로 LOCK_FAILED 발생 시 성공 건수가 줄 수 있음
        assertThat(successCount.get() + lockFailedCount.get())
            .as("모든 스레드가 경합에 참여해야 한다")
            .isEqualTo(2);
    }
}
