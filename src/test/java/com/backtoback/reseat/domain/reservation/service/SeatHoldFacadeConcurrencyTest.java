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
import org.junit.jupiter.api.Disabled;
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
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationResponse;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
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
 * [이슈 #173] Redisson 분산락 Facade 경로 동시성 회귀 테스트.
 *
 * <p> 이 테스트는 SeatHoldFacade를 통해 실제 락 경로를 검증한다.</p>
 * <p> 핵심 검증:
 * - 동일 좌석 N 요청 → 성공 1건, 나머지 409
 * - reservation_seats 행 1건
 * - DB 유니크 위반 0건
 * - 예상 외 예외 0건
 * </p>
 */
@Disabled("테스트 제외")
@Slf4j
@EnabledIfEnvironmentVariable(named = "RUN_CONCURRENCY_TESTS", matches = "true")
@Tag("concurrency")
@ActiveProfiles("test-concurrency")
@SpringBootTest
class SeatHoldFacadeConcurrencyTest {

	private static final int THREAD_COUNT = 10;
	private final List<Long> userIds = new ArrayList<>();
	private final List<Long> tokenIds = new ArrayList<>();
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
	private Long gameId;
	private Long seatId;
	private Long seatZoneId;
	private Long stadiumId;
	private Long homeTeamId;
	private Long awayTeamId;

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
			.title("[이슈 #173] 분산락 동시성 테스트 경기")
			.build();
		gameRepository.save(game);
		gameId = game.getId();

		SeatZone zone = SeatZone.of(stadium, "테스트존", SeatGrade.INFIELD, 18000);
		seatZoneRepository.save(zone);
		seatZoneId = zone.getId();

		Seat seat = Seat.of(stadium, zone, "A", "1", "1");
		seatRepository.save(seat);
		seatId = seat.getId();

		GameSeat gameSeat = GameSeat.builder()
			.game(game)
			.seat(seat)
			.price(18000)
			.status(GameSeatStatus.AVAILABLE)
			.build();
		gameSeatRepository.save(gameSeat);
		targetGameSeatId = gameSeat.getId();

		// 각 사용자에게 유효한 Queue-Token 사전 발급
		for (int i = 0; i < THREAD_COUNT; i++) {
			User user = User.builder()
				.email("facade-concurrency-" + i + "@reseat.com")
				.password("pw")
				.name("테스트유저" + i)
				.phone("010-1111-" + String.format("%04d", i))
				.isVerified(true)
				.role(UserRole.USER)
				.status(UserStatus.ACTIVE)
				.build();
			userRepository.save(user);
			userIds.add(user.getId());

			// validateToken / consumeToken 이 DB 조회하므로 실제 토큰 레코드 필요
			AdmissionToken token = AdmissionToken.of(
				game,
				user,
				"qt_test-token-" + i,
				LocalDateTime.now(),
				LocalDateTime.now().plusMinutes(5));
			admissionTokenRepository.save(token);
			tokenIds.add(token.getId());
		}
	}

	@AfterEach
	void tearDown() {
		admissionTokenRepository.deleteAllById(tokenIds);
		tokenIds.clear();

		reservationSeatRepository.deleteAll();
		reservationRepository.deleteAll();

		gameSeatRepository.deleteAll();
		if (gameId != null)
			gameRepository.deleteById(gameId);
		if (seatId != null)
			seatRepository.deleteById(seatId);
		if (seatZoneId != null)
			seatZoneRepository.deleteById(seatZoneId);
		if (homeTeamId != null)
			teamRepository.deleteById(homeTeamId);
		if (awayTeamId != null)
			teamRepository.deleteById(awayTeamId);
		if (stadiumId != null)
			stadiumRepository.deleteById(stadiumId);

		if (!userIds.isEmpty()) {
			userRepository.deleteAllById(userIds);
			userIds.clear();
		}
	}

	@Test
	@DisplayName("[이슈 #173] 분산락 적용 Facade 경로에서 동일 좌석 N 요청 시 성공 1건, over-booking 0건")
	void should_holdSeatOnce_when_sameSeatRequestsCompeteThroughFacade() throws InterruptedException {
		// given
		CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
		CountDownLatch startLatch = new CountDownLatch(1);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger seatAlreadyHeldCount = new AtomicInteger(0);
		AtomicInteger lockFailedCount = new AtomicInteger(0);
		AtomicInteger dataIntegrityViolationCount = new AtomicInteger(0);
		AtomicInteger unexpectedExceptionCount = new AtomicInteger(0);

		ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

		// when
		for (int i = 0; i < THREAD_COUNT; i++) {
			final Long userId = userIds.get(i);
			final String token = "qt_test-token-" + i;

			executor.submit(() -> {
				readyLatch.countDown();
				try {
					startLatch.await();
					SeatHoldRequest request = new SeatHoldRequest(gameId, List.of(targetGameSeatId));
					ReservationResponse response = seatHoldFacade.holdSeats(userId, token, request);
					assertThat(response.status()).isEqualTo(ReservationStatus.HOLDING);
					successCount.incrementAndGet();
				} catch (com.backtoback.reseat.domain.reservation.exception.SeatAlreadyHeldException e) {
					seatAlreadyHeldCount.incrementAndGet();
				} catch (com.backtoback.reseat.domain.reservation.exception.LockFailedException e) {
					lockFailedCount.incrementAndGet();
				} catch (org.springframework.dao.DataIntegrityViolationException e) {
					dataIntegrityViolationCount.incrementAndGet();
				} catch (Exception e) {
					log.error("[이슈 #173] 예상 외 예외 발생: {}", e.getClass().getSimpleName(), e);
					unexpectedExceptionCount.incrementAndGet();
				}
			});
		}

		readyLatch.await();
		startLatch.countDown();
		executor.shutdown();
		boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);

		// then
		GameSeat finalGameSeat = gameSeatRepository.findById(targetGameSeatId).orElseThrow();
		long reservationSeatRows = reservationSeatRepository.findAll().stream()
			.filter(rs -> rs.getGameSeat().getId().equals(targetGameSeatId))
			.count();

		log.info("════════════════════════════════════════════════════");
		log.info("[이슈 #173] Redisson 분산락 Facade 경로 동시성 검증 수치");
		log.info("  동시 스레드 수                : {}", THREAD_COUNT);
		log.info("  선점 성공 건수                : {}", successCount.get());
		log.info("  SEAT_ALREADY_HELD 건수        : {}", seatAlreadyHeldCount.get());
		log.info("  LOCK_FAILED 건수              : {}", lockFailedCount.get());
		log.info("  DataIntegrityViolation 건수   : {}", dataIntegrityViolationCount.get());
		log.info("  예상 외 예외 건수              : {}", unexpectedExceptionCount.get());
		log.info("  game_seats.status             : {}", finalGameSeat.getStatus());
		log.info("  reservation_seats 행 수        : {}", reservationSeatRows);
		log.info("════════════════════════════════════════════════════");

		assertThat(finished)
			.as("15초 내에 모든 스레드가 종료되지 않았다 — 데드락 또는 타임아웃 의심")
			.isTrue();

		assertThat(successCount.get())
			.as("분산락 적용 후 성공은 정확히 1건이어야 한다")
			.isEqualTo(1);

		assertThat(reservationSeatRows)
			.as("reservation_seats 행은 1건이어야 한다 — over-booking 0건 보장")
			.isEqualTo(1);

		assertThat(finalGameSeat.getStatus())
			.as("최종 좌석 상태는 HELD여야 한다")
			.isEqualTo(GameSeatStatus.HELD);

		assertThat(dataIntegrityViolationCount.get())
			.as("DB 유니크 위반은 0건이어야 한다 — 락이 DB까지 도달하는 요청을 차단해야 한다")
			.isZero();

		assertThat(unexpectedExceptionCount.get())
			.as("예상 외 예외는 0건이어야 한다")
			.isZero();

		assertThat(seatAlreadyHeldCount.get() + lockFailedCount.get())
			.as("실패 건수 합계는 THREAD_COUNT - 1이어야 한다")
			.isEqualTo(THREAD_COUNT - 1);
	}
}
