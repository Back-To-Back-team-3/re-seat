package com.backtoback.reseat.domain.payment.integration;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.order.entity.Order;
import com.backtoback.reseat.domain.payment.dto.request.PaymentRequest;
import com.backtoback.reseat.domain.payment.dto.response.PaymentCreateResponse;
import com.backtoback.reseat.domain.payment.entity.Payment;
import com.backtoback.reseat.domain.payment.repository.PaymentRepository;
import com.backtoback.reseat.domain.payment.service.PaymentService;
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

@Slf4j
@Import(PaymentCreationConcurrencyTest.RedissonTestConfig.class)
@TestPropertySource(properties = "jwt.secret=cGF5bWVudC1jb25jdXJyZW5jeS10ZXN0LXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5n")
@DisplayName("동일 주문 결제 생성 동시성")
class PaymentCreationConcurrencyTest extends BaseIntegrationTest {

    private static final int AMOUNT = 18_000;
    private static final String IDEMPOTENCY_KEY_A = "payment-concurrency-key-a";
    private static final String IDEMPOTENCY_KEY_B = "payment-concurrency-key-b";

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("서로 다른 멱등키로 동시 요청해도 동일 Payment를 반환한다.")
    void convergesConcurrentRequestsToSamePayment() throws Exception {
        // CountDownLatch: 요청을 같은 시점에 출발시킴
        // ExecutorService: 결제 요청 두 건을 서로 다른 스레드에서 실행
        PaymentFixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<PaymentCreateResponse> first
                = executor
                    .submit(() -> requestPayment(fixture.userId(), fixture.orderId(), IDEMPOTENCY_KEY_A, ready, start));
            Future<PaymentCreateResponse> second
                = executor
                    .submit(() -> requestPayment(fixture.userId(), fixture.orderId(), IDEMPOTENCY_KEY_B, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).as("두 결제 요청이 동시 실행 준비를 마치지 못했다.").isTrue();
            start.countDown();

            PaymentCreateResponse firstResponse = first.get(10, TimeUnit.SECONDS);
            PaymentCreateResponse secondResponse = second.get(10, TimeUnit.SECONDS);
            List<Payment> payments = paymentRepository.findAll();

            log.info("══════════════════════════════════════════════════");
            log.info("[B7 해결] 동일 주문 결제 생성 동시성 검증");
            log.info("  첫 번째 응답 paymentId : {}", firstResponse.getPaymentId());
            log.info("  두 번째 응답 paymentId: {}", secondResponse.getPaymentId());
            log.info("  최종 Payment 행 수     : {}", payments.size());
            log.info("══════════════════════════════════════════════════");

            assertThat(firstResponse.getPaymentId()).isEqualTo(secondResponse.getPaymentId());
            assertThat(payments).hasSize(1);
            assertThat(payments.get(0).getOrder().getId()).isEqualTo(fixture.orderId());
            assertThat(payments.get(0).getIdempotencyKey()).isIn(IDEMPOTENCY_KEY_A, IDEMPOTENCY_KEY_B);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private PaymentCreateResponse requestPayment(
        Long userId,
        Long orderId,
        String idempotencyKey,
        CountDownLatch ready,
        CountDownLatch start
    )
        throws InterruptedException {
        PaymentRequest request = new PaymentRequest();
        ReflectionTestUtils.setField(request, "orderId", orderId);

        log.info("결제 요청 준비 완료 | idempotencyKey: {}", idempotencyKey);
        ready.countDown();
        start.await();
        return paymentService.requestPayment(userId, idempotencyKey, request);
    }

    private PaymentFixture createFixture() {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();

            Stadium stadium = Stadium.of("테스트 구장", "테스트 구장 주소", 10_000);
            entityManager.persist(stadium);

            Team homeTeam = Team.of("홈팀", stadium);
            Team awayTeam = Team.of("원정팀", stadium);
            entityManager.persist(homeTeam);
            entityManager.persist(awayTeam);

            Game game
                = Game
                    .builder()
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .stadium(stadium)
                    .gameAt(now.plusDays(7))
                    .bookingOpenAt(now.minusDays(1))
                    .bookingCloseAt(now.plusDays(6))
                    .bookingStatus(BookingStatus.OPEN)
                    .title("테스트 경기")
                    .build();
            entityManager.persist(game);

            User user
                = User
                    .builder()
                    .email("email@example.com")
                    .password("test1234!")
                    .name("테스트 사용자")
                    .phone("010-9876-5432")
                    .isVerified(true)
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build();
            entityManager.persist(user);

            Reservation reservation
                = Reservation
                    .builder()
                    .reservationNo("RSV-TEST")
                    .user(user)
                    .game(game)
                    .status(ReservationStatus.HOLDING)
                    .holdExpiresAt(now.plusMinutes(10))
                    .build();
            entityManager.persist(reservation);

            Order order = Order.of("ORD-TEST", user, reservation, AMOUNT, now.plusMinutes(8));
            entityManager.persist(order);
            entityManager.flush();

            return new PaymentFixture(user.getId(), order.getId());
        });
    }

    private record PaymentFixture(Long userId, Long orderId) {
    }

    @TestConfiguration
    static class RedissonTestConfig {

        @Bean(destroyMethod = "shutdown")
        RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port
        ) {
            Config config = new Config();
            config.useSingleServer().setAddress("redis://" + host + ":" + port);
            return Redisson.create(config);
        }
    }
}
