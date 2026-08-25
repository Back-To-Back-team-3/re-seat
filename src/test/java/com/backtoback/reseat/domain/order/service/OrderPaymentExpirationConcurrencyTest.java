package com.backtoback.reseat.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.order.entity.OrderStatus;
import com.backtoback.reseat.domain.order.repository.OrderRepository;
import com.backtoback.reseat.domain.reservation.entity.Reservation;
import com.backtoback.reseat.domain.reservation.entity.ReservationStatus;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.global.common.BaseIntegrationTest;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

/**
 * [Issue #279] 결제 완료와 주문 만료가 동시에 실행될 때 주문 상태를 검증한다.
 * <p>같은 CREATED 주문을 두 조건부 UPDATE가 변경하려고 하면 MySQL의 주문 행 락에 따라 순서대로 실행된다.
 * 먼저 커밋된 상태는 나중 작업이 덮어쓰지 않아야 한다.</p>
 */
@Slf4j
@Tag("concurrency")
@DisplayName("[Issue #279] 결제 완료와 주문 만료 상태 전이 동시성 테스트")
class OrderPaymentExpirationConcurrencyTest extends BaseIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 22, 20, 0);
    private static final LocalDateTime PAYMENT_DEADLINE = NOW;
    // 결제 기한 조건은 통과시키고 CREATED 상태 조건만 검증하도록 기한보다 1마이크로초(μs) 앞선 시간을 사용한다.
    private static final LocalDateTime PAYMENT_COMPLETION_TIME = PAYMENT_DEADLINE.minusNanos(1_000);
    private static final int TOTAL_AMOUNT = 34_000;
    private static final long TIMEOUT_SECONDS = 10L;

    // test 프로파일에 Redisson 설정이 없으므로 PaymentService의 의존성만 Mock으로 대체한다.
    @MockitoBean
    private RedissonClient redissonClient;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 두 작업이 같은 주문을 조회할 수 있도록 주문 ID를 전달한다.
     *
     * @param orderId 동시 상태 전이 대상 주문 ID
     */
    private record OrderConcurrencyFixture(Long orderId) {
    }

    /**
     * 동시성 검증에 사용할 CREATED 주문을 별도 트랜잭션으로 저장한다.
     * <p>작업 스레드가 시작되기 전에 커밋해 두 작업이 같은 주문을 조회할 수 있게 한다.</p>
     *
     * @return 동시성 검증에 사용할 주문 fixture
     */
    private OrderConcurrencyFixture createOrderFixture() {

        return transactionTemplate.execute(transactionStatus -> {
            Stadium stadium = Stadium.of("주문 상태 전이 동시성 테스트 구장", "테스트시 테스트구", 10_000);
            entityManager.persist(stadium);

            Team homeTeam = Team.of("동시성 테스트 홈팀", stadium);
            Team awayTeam = Team.of("동시성 테스트 원정팀", stadium);
            entityManager.persist(homeTeam);
            entityManager.persist(awayTeam);

            Game game
                = Game
                    .builder()
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .stadium(stadium)
                    .gameAt(NOW.plusDays(7))
                    .bookingOpenAt(NOW.minusDays(1))
                    .bookingCloseAt(NOW.plusDays(6))
                    .bookingStatus(BookingStatus.OPEN)
                    .title("결제 완료와 주문 만료 상태 전이 동시성 테스트 경기")
                    .build();
            entityManager.persist(game);

            User user
                = User
                    .builder()
                    .email("order-state-concurrency@test.com")
                    .password("test")
                    .name("테스트 사용자")
                    .phone("010-1234-5678")
                    .isVerified(true)
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build();
            entityManager.persist(user);

            Reservation reservation
                = Reservation
                    .builder()
                    .reservationNo("RSV-ORDER-STATE-CONCURRENCY")
                    .user(user)
                    .game(game)
                    .status(ReservationStatus.HOLDING)
                    .holdExpiresAt(PAYMENT_DEADLINE.plusMinutes(2))
                    .build();
            entityManager.persist(reservation);

            Order order = Order.of("ORD-ORDER-STATE-CONCURRENCY", user, reservation, TOTAL_AMOUNT, PAYMENT_DEADLINE);
            entityManager.persist(order);
            entityManager.flush();

            return new OrderConcurrencyFixture(order.getId());
        });
    }

    /**
     * latch 신호를 최대 10초 기다린다.
     * <p>시간이 초과되거나 스레드가 중단되면 예외를 발생시킨다.</p>
     *
     * @param latch 기다릴 신호
     * @param timeoutMessage 신호를 받지 못했을 때 사용할 메시지
     */
    private void awaitLatch(CountDownLatch latch, String timeoutMessage) {

        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(timeoutMessage);
            }
        } catch (InterruptedException e) {
            // 상위 실행 흐름에서도 중단을 확인할 수 있도록 인터럽트 상태를 복원한다.
            Thread.currentThread().interrupt();
            throw new IllegalStateException(timeoutMessage, e);
        }
    }

    /**
     * 두 동시성 테스트의 실행 조건과 UPDATE 결과를 같은 형식으로 출력한다.
     *
     * @param title 먼저 커밋된 작업을 나타내는 로그 제목
     * @param expiredCount 만료 UPDATE 변경 행 수
     * @param paidCount 결제 완료 UPDATE 변경 행 수
     * @param finalStatus 최종 주문 상태
     */
    private void logConcurrencyResult(String title, int expiredCount, int paidCount, OrderStatus finalStatus) {

        log.info("══════════════════════════════════════════════════");
        log.info(title);
        log.info("  결제 기한                    : {}", PAYMENT_DEADLINE);
        log.info("  만료 기준 시간               : {}", NOW);
        log.info("  결제 완료 기준 시간          : {}", PAYMENT_COMPLETION_TIME);
        log.info("  만료 UPDATE 변경 행 수       : {}", expiredCount);
        log.info("  결제 완료 UPDATE 변경 행 수  : {}", paidCount);
        log.info("  최종 주문 상태               : {}", finalStatus);
        log.info("══════════════════════════════════════════════════");
    }

    @Test
    @DisplayName("[Issue #279] 만료 UPDATE가 먼저 커밋되면 결제 완료 UPDATE가 EXPIRED 상태를 PAID로 덮어쓰지 못한다.")
    void should_keepExpiredOrder_when_expirationUpdateCommitsFirst() throws Exception {

        // given
        OrderConcurrencyFixture fixture = createOrderFixture();
        Long orderId = fixture.orderId();

        // 만료 UPDATE → 결제 완료 UPDATE → 만료 트랜잭션 커밋 순서를 제어한다.
        CountDownLatch expireDoneLatch = new CountDownLatch(1);
        CountDownLatch paymentStartedLatch = new CountDownLatch(1);
        CountDownLatch releaseExpireLatch = new CountDownLatch(1);

        // 두 작업을 별도 스레드에서 실행한다.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // when
        try {
            // 만료 UPDATE 후 주문 행 락을 유지한 채 트랜잭션 해제 신호를 기다린다.
            Future<Integer> expireFuture = executor.submit(() -> transactionTemplate.execute(transactionStatus -> {
                int updatedCount
                    = orderRepository.expireCreatedOrder(orderId, NOW, OrderStatus.CREATED, OrderStatus.EXPIRED);

                expireDoneLatch.countDown();
                awaitLatch(releaseExpireLatch, "만료 트랜잭션 해제 신호를 제한 시간 안에 받지 못했다.");

                return updatedCount;
            }));

            // 만료 UPDATE 완료 후 같은 주문에 결제 완료 UPDATE를 시도한다.
            Future<Integer> paymentFuture = executor.submit(() -> {
                awaitLatch(expireDoneLatch, "만료 UPDATE 완료 신호를 제한 시간 안에 받지 못했다.");
                return transactionTemplate.execute(transactionStatus -> {
                    // 결제 완료 UPDATE 호출 직전을 테스트 스레드에 알린다.
                    paymentStartedLatch.countDown();

                    // 만료 트랜잭션의 주문 행 락이 풀릴 때까지 대기한다.
                    return orderRepository
                        .completeCreatedOrder(orderId, PAYMENT_COMPLETION_TIME, OrderStatus.CREATED, OrderStatus.PAID);
                });
            });
            // 결제 완료 UPDATE 호출 직전까지 기다린다.
            awaitLatch(paymentStartedLatch, "결제 완료 UPDATE 시작 신호를 제한 시간 안에 받지 못했다.");

            // 500ms 동안 완료되지 않으면 결제 완료 UPDATE가 주문 행 락을 기다리고 있는 것이다.
            assertThrows(TimeoutException.class, () -> paymentFuture.get(500, TimeUnit.MILLISECONDS));

            // 만료 트랜잭션을 먼저 커밋하도록 대기를 해제한다.
            releaseExpireLatch.countDown();

            // 두 작업의 완료를 기다리고 각각의 변경 행 수를 받는다.
            int expiredCount = expireFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int paidCount = paymentFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // DB에서 최종 주문 상태를 다시 조회한다.
            Order finalOrder = orderRepository.findById(orderId).orElseThrow();

            // then
            logConcurrencyResult("[Issue #279] 만료 UPDATE가 먼저 커밋된 경우", expiredCount, paidCount, finalOrder.getStatus());

            assertThat(expiredCount).as("만료 UPDATE는 CREATED 주문 1건을 EXPIRED로 변경해야 한다").isEqualTo(1);
            assertThat(paidCount).as("결제 완료 UPDATE는 EXPIRED 상태를 PAID로 덮어쓰지 않아야 한다").isEqualTo(0);
            assertThat(finalOrder.getStatus()).as("최종 주문 상태는 EXPIRED를 유지해야 한다").isEqualTo(OrderStatus.EXPIRED);
        } finally {
            // 실패해도 대기 중인 작업을 해제하고 스레드를 종료한다.
            releaseExpireLatch.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("[Issue #279] 결제 완료 UPDATE가 먼저 커밋되면 만료 UPDATE가 PAID 상태를 EXPIRED로 덮어쓰지 못한다.")
    void should_keepPaidOrder_when_paymentUpdateCommitsFirst() throws Exception {

        // given
        OrderConcurrencyFixture fixture = createOrderFixture();
        Long orderId = fixture.orderId();

        // 결제 완료 UPDATE → 만료 UPDATE → 결제 완료 트랜잭션 커밋 순서를 제어한다.
        CountDownLatch paymentDoneLatch = new CountDownLatch(1);
        CountDownLatch expireStartedLatch = new CountDownLatch(1);
        CountDownLatch releasePaymentLatch = new CountDownLatch(1);

        // 두 작업을 별도 스레드에서 실행한다.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // when
        try {
            // 결제 완료 UPDATE 후 주문 행 락을 유지한 채 트랜잭션 해제 신호를 기다린다.
            Future<Integer> paymentFuture = executor.submit(() -> transactionTemplate.execute(transactionStatus -> {
                int updatedCount
                    = orderRepository
                        .completeCreatedOrder(orderId, PAYMENT_COMPLETION_TIME, OrderStatus.CREATED, OrderStatus.PAID);

                paymentDoneLatch.countDown();
                awaitLatch(releasePaymentLatch, "결제 완료 트랜잭션 해제 신호를 제한 시간 안에 받지 못했다.");

                return updatedCount;
            }));

            // 결제 완료 UPDATE 완료 후 같은 주문에 만료 UPDATE를 시도한다.
            Future<Integer> expireFuture = executor.submit(() -> {
                awaitLatch(paymentDoneLatch, "결제 완료 UPDATE 완료 신호를 제한 시간 안에 받지 못했다.");
                return transactionTemplate.execute(transactionStatus -> {
                    // 만료 UPDATE 호출 직전을 테스트 스레드에 알린다.
                    expireStartedLatch.countDown();

                    // 결제 완료 트랜잭션의 주문 행 락이 풀릴 때까지 대기한다.
                    return orderRepository.expireCreatedOrder(orderId, NOW, OrderStatus.CREATED, OrderStatus.EXPIRED);
                });
            });

            // 만료 UPDATE 호출 직전까지 기다린다.
            awaitLatch(expireStartedLatch, "만료 UPDATE 호출 직전 신호를 제한 시간 안에 받지 못했다.");

            // 500ms 동안 완료되지 않으면 만료 UPDATE가 주문 행 락을 기다리고 있는 것이다.
            assertThrows(TimeoutException.class, () -> expireFuture.get(500, TimeUnit.MILLISECONDS));

            // 결제 완료 트랜잭션을 먼저 커밋하도록 대기를 해제한다.
            releasePaymentLatch.countDown();

            // 두 작업의 완료를 기다리고 각각의 변경 행 수를 받는다.
            int paidCount = paymentFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int expiredCount = expireFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // DB에서 최종 주문 상태를 다시 조회한다.
            Order finalOrder = orderRepository.findById(orderId).orElseThrow();

            // then
            logConcurrencyResult(
                "[Issue #279] 결제 완료 UPDATE가 먼저 커밋된 경우",
                expiredCount,
                paidCount,
                finalOrder.getStatus()
            );

            assertThat(paidCount).as("결제 완료 UPDATE는 CREATED 주문 1건을 PAID로 변경해야 한다").isEqualTo(1);
            assertThat(expiredCount).as("만료 UPDATE는 PAID 상태를 EXPIRED로 덮어쓰지 않아야 한다").isEqualTo(0);
            assertThat(finalOrder.getStatus()).as("최종 주문 상태는 PAID를 유지해야 한다").isEqualTo(OrderStatus.PAID);
        } finally {
            // 실패해도 대기 중인 작업을 해제하고 스레드를 종료한다.
            releasePaymentLatch.countDown();
            executor.shutdownNow();
        }
    }
}
