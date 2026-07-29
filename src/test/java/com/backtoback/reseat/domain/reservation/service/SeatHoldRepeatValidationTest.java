package com.backtoback.reseat.domain.reservation.service;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [이슈 #176] TC-001 over-booking 0건 대량 반복 검증.
 *
 * <p>동일 좌석 동시 100요청 × REPEAT_COUNT회 반복 시 매 회차 성공 1건, over-booking 0건을 정량으로 증명한다.
 * SeatHoldFacadeConcurrencyTest(이슈 #173, 10스레드 1회) 패턴을 계승하여 스레드 수와 반복 횟수를 확장한다.
 *
 * <p>실행 조건: 환경변수 RUN_CONCURRENCY_TESTS=true + test-concurrency 프로파일(MySQL)
 */
@Disabled("MySQL 환경 전용 — RUN_CONCURRENCY_TESTS=true 환경변수 설정 후 실행")
@Slf4j
@EnabledIfEnvironmentVariable(named = "RUN_CONCURRENCY_TESTS", matches = "true")
@Tag("concurrency")
@ActiveProfiles("test-concurrency")
@SpringBootTest
class SeatHoldRepeatValidationTest {

    // TC-001 기준: 동시 100요청 × 10회 이상 반복
    private static final int THREAD_COUNT = 100;
    private static final int REPEAT_COUNT = 10;
    // 각 회차 스레드 종료 대기 제한 (초)
    private static final int AWAIT_SECONDS = 30;

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

    // @Transactional 금지 — 멀티스레드 환경에서 트랜잭션 공유 불가. @AfterEach 수동 정리.
    private Long targetGameSeatId;
    private Long gameId;
    private Long seatId;
    private Long seatZoneId;
    private Long stadiumId;
    private Long homeTeamId;
    private Long awayTeamId;
    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 구장
        Stadium stadium = Stadium.of("테스트 구장", "서울시 테스트구 1", 10000);
        stadiumRepository.save(stadium);
        stadiumId = stadium.getId();

        // 구단
        Team homeTeam = Team.of("홈팀", stadium);
        Team awayTeam = Team.of("원정팀", stadium);
        teamRepository.save(homeTeam);
        teamRepository.save(awayTeam);
        homeTeamId = homeTeam.getId();
        awayTeamId = awayTeam.getId();

        // 경기
        Game game = Game.builder()
            .homeTeam(homeTeam)
            .awayTeam(awayTeam)
            .stadium(stadium)
            .gameAt(LocalDateTime.now().plusDays(7))
            .bookingOpenAt(LocalDateTime.now().minusHours(1))
            .bookingCloseAt(LocalDateTime.now().plusDays(6))
            .bookingStatus(BookingStatus.OPEN)
            .title("[이슈 #176] over-booking 대량 반복 검증 경기")
            .build();
        gameRepository.save(game);
        gameId = game.getId();

        // 구역 + 물리 좌석
        SeatZone zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, 18000);
        seatZoneRepository.save(zone);
        seatZoneId = zone.getId();

        Seat seat = Seat.of(stadium, zone, "A", "1", "1");
        seatRepository.save(seat);
        seatId = seat.getId();

        // 경기 좌석 재고 (테스트 대상 — AVAILABLE 1건)
        GameSeat gameSeat = GameSeat.builder()
            .game(game)
            .seat(seat)
            .price(18000)
            .status(GameSeatStatus.AVAILABLE)
            .build();
        gameSeatRepository.save(gameSeat);
        targetGameSeatId = gameSeat.getId();

        // 사용자 THREAD_COUNT명 사전 생성. 토큰은 회차마다 resetForRound()에서 발급.
        for (int i = 0; i < THREAD_COUNT; i++) {
            User user = User.builder()
                .email("repeat-validation-" + i + "@reseat.com")
                .password("pw")
                .name("테스트유저" + i)
                .phone("010-9999-" + String.format("%04d", i))
                .isVerified(true)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
            userRepository.save(user);
            userIds.add(user.getId());
        }
    }

    // FK 역참조 역순 삭제: admission_tokens → reservation_seats → reservations
    // → game_seats → games → seats → seat_zones → teams → stadiums → users
    @AfterEach
    void tearDown() {
        admissionTokenRepository.deleteAll();
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        gameSeatRepository.deleteAll();
        if (gameId != null) gameRepository.deleteById(gameId);
        if (seatId != null) seatRepository.deleteById(seatId);
        if (seatZoneId != null) seatZoneRepository.deleteById(seatZoneId);
        if (homeTeamId != null) teamRepository.deleteById(homeTeamId);
        if (awayTeamId != null) teamRepository.deleteById(awayTeamId);
        if (stadiumId != null) stadiumRepository.deleteById(stadiumId);
        if (!userIds.isEmpty()) {
            userRepository.deleteAllById(userIds);
            userIds.clear();
        }
    }

    /**
     * 각 회차가 끝난 뒤, 다음 회차 시작 전에 실행한다.
     * admission_tokens 삭제 → reservation_seats/reservations 삭제 → game_seats AVAILABLE 리셋 → 토큰 신규 발급.
     *
     * <p>consumeToken이 holdSeats 성공 후 토큰을 USED로 전이시키므로 회차마다 토큰을 새로 발급해야 한다.
     */
    private void resetForRound(int round) {
        admissionTokenRepository.deleteAll();
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();

        // releaseExpiredSeats()는 hold_expires_at 조건이 있어 사용 불가 → findById → available() → save
        GameSeat gameSeat = gameSeatRepository.findById(targetGameSeatId).orElseThrow();
        gameSeat.available();
        gameSeatRepository.save(gameSeat);

        // 토큰 문자열: "qt_repeat-r{회차}-{인덱스}" — 전체 회차에서 uk_admission_tokens_token 유니크 보장
        Game game = gameRepository.findById(gameId).orElseThrow();
        for (int i = 0; i < THREAD_COUNT; i++) {
            User user = userRepository.findById(userIds.get(i)).orElseThrow();
            admissionTokenRepository.save(AdmissionToken.of(
                game,
                user,
                "qt_repeat-r" + round + "-" + i,
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(5)
            ));
        }
    }

    @Test
    @DisplayName("[TC-001] 동일 좌석 동시 100요청 × " + REPEAT_COUNT + "회 반복 — 매 회차 성공 1건, over-booking 0건")
    void should_holdSeatOnce_when_hundredThreadsCompeteRepeatedly() throws InterruptedException {
        for (int round = 1; round <= REPEAT_COUNT; round++) {

            // 회차 시작 전: DB 초기화 + 토큰 신규 발급
            resetForRound(round);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger seatAlreadyHeldCount = new AtomicInteger(0);
            AtomicInteger lockFailedCount = new AtomicInteger(0);
            AtomicInteger dataIntegrityViolationCount = new AtomicInteger(0);
            AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

            // readyLatch: 모든 스레드 준비 완료, startLatch: 동시 출발
            CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

            final int currentRound = round;

            for (int i = 0; i < THREAD_COUNT; i++) {
                final Long userId = userIds.get(i);
                final String token = "qt_repeat-r" + currentRound + "-" + i;

                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        seatHoldFacade.holdSeats(userId, token, new SeatHoldRequest(gameId, List.of(targetGameSeatId)));
                        successCount.incrementAndGet();
                    } catch (SeatAlreadyHeldException e) {
                        // SEAT_ALREADY_HELD(409) — 락 획득 후 상태 재검증에서 차단. 정상 동작.
                        seatAlreadyHeldCount.incrementAndGet();
                    } catch (LockFailedException e) {
                        // LOCK_FAILED(409) — 분산락 획득 타임아웃. 정상 동작.
                        lockFailedCount.incrementAndGet();
                    } catch (DataIntegrityViolationException e) {
                        // uk_game_seats_game_seat 위반 — 분산락이 정상 동작한다면 0건이어야 한다.
                        log.error("[TC-001] 회차 {}/{} DataIntegrityViolation — 락 설계 점검 필요: {}",
                            currentRound, REPEAT_COUNT, e.getMessage());
                        dataIntegrityViolationCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("[TC-001] 회차 {}/{} 예상 외 예외: {}",
                            currentRound, REPEAT_COUNT, e.getClass().getSimpleName(), e);
                        unexpectedExceptionCount.incrementAndGet();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            executor.shutdown();
            boolean finished = executor.awaitTermination(AWAIT_SECONDS, TimeUnit.SECONDS);

            // 회차별 수치 로그
            GameSeat finalGameSeat = gameSeatRepository.findById(targetGameSeatId).orElseThrow();
            long reservationSeatRows = reservationSeatRepository.findAll().stream()
                .filter(rs -> rs.getGameSeat().getId().equals(targetGameSeatId))
                .count();

            log.info("════════════════════════════════════════════════════");
            log.info("[TC-001] over-booking 대량 반복 검증 — 회차 {}/{}", currentRound, REPEAT_COUNT);
            log.info("  동시 스레드 수               : {}", THREAD_COUNT);
            log.info("  선점 성공 건수               : {}", successCount.get());
            log.info("  SEAT_ALREADY_HELD 건수      : {}", seatAlreadyHeldCount.get());
            log.info("  LOCK_FAILED 건수            : {}", lockFailedCount.get());
            log.info("  DataIntegrityViolation 건수 : {}", dataIntegrityViolationCount.get());
            log.info("  예상 외 예외 건수            : {}", unexpectedExceptionCount.get());
            log.info("  game_seats.status           : {}", finalGameSeat.getStatus());
            log.info("  reservation_seats 행 수     : {}", reservationSeatRows);
            log.info("  {}초 내 종료 여부            : {}", AWAIT_SECONDS, finished);
            log.info("════════════════════════════════════════════════════");

            // 회차별 어시션
            assertThat(finished)
                .as("[회차 %d] %d초 내 모든 스레드가 종료되지 않았다 — 데드락 또는 타임아웃 의심", currentRound, AWAIT_SECONDS)
                .isTrue();
            assertThat(successCount.get())
                .as("[회차 %d] 선점 성공은 정확히 1건이어야 한다 — over-booking 발생 시 > 1", currentRound)
                .isEqualTo(1);
            assertThat(reservationSeatRows)
                .as("[회차 %d] reservation_seats 행은 1건이어야 한다", currentRound)
                .isEqualTo(1);
            assertThat(finalGameSeat.getStatus())
                .as("[회차 %d] 최종 game_seats.status는 HELD여야 한다", currentRound)
                .isEqualTo(GameSeatStatus.HELD);
            assertThat(dataIntegrityViolationCount.get())
                .as("[회차 %d] DataIntegrityViolation은 0건이어야 한다", currentRound)
                .isZero();
            assertThat(unexpectedExceptionCount.get())
                .as("[회차 %d] 예상 외 예외는 0건이어야 한다", currentRound)
                .isZero();
        }

        log.info("════════════════════════════════════════════════════");
        log.info("[TC-001] 전 회차 완료 — {}회 × {}스레드 over-booking 0건 확인", REPEAT_COUNT, THREAD_COUNT);
        log.info("════════════════════════════════════════════════════");
    }
}
