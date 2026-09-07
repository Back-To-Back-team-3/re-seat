package com.backtoback.reseat.domain.seatinventory.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.seatinventory.entity.GameSeat;
import com.backtoback.reseat.domain.seatinventory.exception.SeatInventoryAlreadyOpenedException;
import com.backtoback.reseat.domain.seatinventory.repository.GameSeatRepository;
import com.backtoback.reseat.domain.stadium.entity.Seat;
import com.backtoback.reseat.domain.stadium.entity.SeatGrade;
import com.backtoback.reseat.domain.stadium.entity.SeatZone;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.global.common.BaseIntegrationTest;

import jakarta.persistence.EntityManager;

/**
 * 좌석 재고 오픈 동시성 테스트.
 * <p>
 * 클래스 레벨 @Transactional을 두지 않는다 — 여러 스레드가 각자 실제 커밋을 해야
 * 동시 오픈 경합이 재현되기 때문이다. 대신 @BeforeEach의 픽스처 생성만
 * TransactionTemplate으로 별도 트랜잭션을 열어 커밋한다.
 */
@ActiveProfiles(
    {
        "test",
        "redis-test"
    }
)
@DisplayName("GameSeatCreateService 재고 오픈 동시성")
class GameSeatCreateConcurrencyTest extends BaseIntegrationTest {

    private static final int CONCURRENT_REQUEST_COUNT = 10;
    private static final int EXPECTED_SEAT_COUNT = 500;

    @Autowired
    private GameSeatCreateService gameSeatCreateService;

    @Autowired
    private GameSeatRepository gameSeatRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long gameId;

    @BeforeEach
    void setUp() {
        // 픽스처는 이 트랜잭션 안에서만 만들고 즉시 커밋한다.
        // 테스트 메서드 자체는 @Transactional이 없으므로, 여기서 커밋해두지 않으면
        // 동시 실행되는 스레드들이 아직 존재하지 않는 경기를 조회하게 된다.
        gameId = transactionTemplate.execute(status -> {
            Stadium stadium = persistStadium("동시성테스트구장");
            SeatZone zone = persistSeatZone(stadium, "내야존", SeatGrade.INFIELD, 15000);
            persistActiveSeats(stadium, zone, EXPECTED_SEAT_COUNT);

            Team homeTeam = persistTeam("동시성홈팀", stadium);
            Team awayTeam = persistTeam("동시성원정팀", stadium);

            return persistGame(homeTeam, awayTeam, stadium).getId();
        });
    }

    @DisplayName("동일 경기에 재고 오픈이 동시에 10건 들어오면 성공은 1건, 나머지 9건은 409로 차단된다")
    @Test
    void should_openOnce_when_concurrentOpenRequested() throws InterruptedException {
        // given
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger alreadyOpenedCount = new AtomicInteger();
        AtomicInteger unexpectedCount = new AtomicInteger();

        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_REQUEST_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENT_REQUEST_COUNT);

        // when — CONCURRENT_REQUEST_COUNT개 스레드가 동시에 같은 경기의 재고 오픈을 호출한다.
        for (int i = 0; i < CONCURRENT_REQUEST_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    gameSeatCreateService.openInventory(gameId);
                    successCount.incrementAndGet();
                } catch (SeatInventoryAlreadyOpenedException e) {
                    alreadyOpenedCount.incrementAndGet();
                } catch (Exception e) {
                    // 여기로 떨어지면 500(DataIntegrityViolationException 노출 등) 회귀를 의미한다.
                    unexpectedCount.incrementAndGet();
                    System.err.println("예상치 못한 예외 발생: " + e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(alreadyOpenedCount.get()).isEqualTo(CONCURRENT_REQUEST_COUNT - 1);
        assertThat(unexpectedCount.get()).isZero();

        List<GameSeat> gameSeats = gameSeatRepository.findAllByGameIdWithSeatAndZone(gameId);
        assertThat(gameSeats).hasSize(EXPECTED_SEAT_COUNT); // 중복 생성 없이 정확히 1회분만 생성됨
    }

    private Team persistTeam(String name, Stadium homeStadium) {
        Team team = Team.of(name, homeStadium);
        entityManager.persist(team);
        return team;
    }

    private Stadium persistStadium(String name) {
        Stadium stadium = Stadium.of(name, "테스트주소", 30000);
        entityManager.persist(stadium);
        return stadium;
    }

    private SeatZone persistSeatZone(Stadium stadium, String name, SeatGrade grade, int basePrice) {
        SeatZone zone = SeatZone.of(stadium, name, grade, basePrice);
        entityManager.persist(zone);
        return zone;
    }

    private void persistActiveSeats(Stadium stadium, SeatZone zone, int count) {
        for (int i = 0; i < count; i++) {
            String seatRow = String.valueOf((i / 25) + 1);
            String seatNumber = String.valueOf((i % 25) + 1);
            Seat seat = Seat.of(stadium, zone, "A", seatRow, seatNumber);
            entityManager.persist(seat);
        }
    }

    private Game persistGame(Team homeTeam, Team awayTeam, Stadium stadium) {
        LocalDateTime gameAt = LocalDateTime.now().plusDays(1);
        Game game
            = Game
                .builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium(stadium)
                .gameAt(gameAt)
                .bookingOpenAt(gameAt.minusDays(7))
                .bookingCloseAt(gameAt.minusHours(1))
                .bookingStatus(BookingStatus.SCHEDULED)
                .build();
        entityManager.persist(game);
        return game;
    }
}
