package com.backtoback.reseat.domain.queue.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.dto.event.QueueEntryRequestedEvent;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistory;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.queue.repository.QueueEntryHistoryRepository;
import com.backtoback.reseat.domain.stadium.entity.Stadium;
import com.backtoback.reseat.domain.stadium.repository.StadiumRepository;
import com.backtoback.reseat.domain.team.entity.Team;
import com.backtoback.reseat.domain.team.repository.TeamRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.entity.UserRole;
import com.backtoback.reseat.domain.user.entity.UserStatus;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import com.backtoback.reseat.global.common.BaseIntegrationTest;
import com.backtoback.reseat.global.service.TestDatabaseCleanUpService;

/**
 * Queue의 DB 대기 이력, Queue-Token과 Redis 대기열 간 정합성을 검증한다.
 */
@DisplayName("QueueConsistency")
public class QueueConsistencyTest extends BaseIntegrationTest {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private QueueEntryHistoryRepository queueEntryHistoryRepository;
    @Autowired
    private AdmissionTokenRepository admissionTokenRepository;

    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private StadiumRepository stadiumRepository;

    @Autowired
    private QueueService queueService;
    @Autowired
    private AdmissionTokenService admissionTokenService;

    @Autowired
    private TestDatabaseCleanUpService testDatabaseCleanUpService;

    private User user;
    private Game game;

    /**
     * 각 테스트 전 Queue 흐름에 필요한 Stadium, Team, User, Game을 저장한다.
     */
    @BeforeEach
    void setUp() {

        Stadium stadium = stadiumRepository.save(Stadium.of("테스트 구장", "테스트시 테스트구", 10_000));

        Team team = teamRepository.save(Team.of("테스트팀", stadium));

        user
            = userRepository
                .save(
                    User
                        .builder()
                        .email("test-user@test.com")
                        .password("test")
                        .name("테스트 사용자")
                        .phone("010-1234-5678")
                        .isVerified(true)
                        .role(UserRole.USER)
                        .status(UserStatus.ACTIVE)
                        .build()
                );

        game
            = gameRepository
                .save(
                    Game
                        .builder()
                        .homeTeam(team)
                        .awayTeam(team)
                        .stadium(stadium)
                        .gameAt(LocalDateTime.now().plusDays(1))
                        .bookingOpenAt(LocalDateTime.now().minusHours(1))
                        .bookingCloseAt(LocalDateTime.now().plusHours(5))
                        .title("테스트 경기")
                        .build()
                );
    }

    /**
     * 각 테스트 후 DB와 Redis에 남은 Queue 테스트 데이터를 정리한다.
     */
    @AfterEach
    void tearDown() {

        testDatabaseCleanUpService.cleanUpAll();
    }

    /**
     * 요청 시간으로 현재 사용자와 경기의 대기열 진입 이벤트를 생성한다.
     *
     * @param requestedAt 대기열 진입 요청 시간
     * @return 대기열 진입 요청 이벤트
     */
    private QueueEntryRequestedEvent queueEntryEvent(Instant requestedAt) {

        return new QueueEntryRequestedEvent(UUID.randomUUID(), game.getId(), user.getId(), requestedAt);
    }

    /**
     * Queue-Key에 해당하는 DB 대기 이력을 조회한다.
     *
     * @param queueKey 조회할 대기 이력의 Queue-Key
     * @return 조회된 대기 이력
     */
    private QueueEntryHistory findQueueEntryHistory(String queueKey) {

        return queueEntryHistoryRepository.findByQueueKey(queueKey).orElseThrow();
    }

    /**
     * Redis 대기열에서 사용자의 점수를 조회한다.
     *
     * @param redisKey 경기별 Redis 대기열 Key
     * @param redisMember 사용자 Redis Member
     * @return Redis 대기열 점수, 등록되지 않은 경우 null
     */
    private Double queueScore(String redisKey, String redisMember) {

        return redisTemplate.opsForZSet().score(redisKey, redisMember);
    }

    /**
     * 현재 경기의 자동 입장 분산 락을 정상 획득하도록 Mock 동작을 설정한다.
     */
    private void givenAdmitLockAcquired() throws InterruptedException {

        String lockKey = "lock:queue:admit:" + game.getId();

        RLock lock = mock(RLock.class);
        given(redissonClient.getLock(lockKey)).willReturn(lock);
        given(lock.tryLock(1L, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
    }

    /**
     * 요청 시간으로 현재 사용자와 경기의 대기열 진입 이벤트를 등록한다.
     *
     * @param requestedAt 대기열 진입 요청 시간
     */
    private void registerQueueEntry(Instant requestedAt) {

        QueueEntryRequestedEvent event = queueEntryEvent(requestedAt);
        queueService.registerQueueEntry(event);
    }

    @Test
    @DisplayName("자동 입장 후 DB와 Redis의 대기열 상태가 일치한다.")
    void admit_keepsQueueStateConsistent() throws InterruptedException {

        // given
        // 실제 Queue 진입 흐름으로 DB 이력과 Redis 대기열을 함께 준비한다.
        givenAdmitLockAcquired();

        String redisKey = "queue:game:" + game.getId();
        String redisMember = "user:" + user.getId();
        String queueKey = redisKey + ":" + redisMember;

        registerQueueEntry(Instant.now());

        // when
        int admittedCount = admissionTokenService.admit(game.getId(), 1);

        // then
        // 자동 입장은 DB 이력과 Queue-Token을 커밋한 뒤 Redis 대기열에서 사용자를 제거한다.
        assertThat(admittedCount).isEqualTo(1);
        QueueEntryHistory admittedHistory = findQueueEntryHistory(queueKey);
        assertThat(admittedHistory.getStatus()).isEqualTo(QueueEntryHistoryStatus.ADMITTED);
        assertThat(
            admissionTokenRepository
                .findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(
                    game.getId(),
                    user.getId(),
                    AdmissionTokenStatus.ACTIVE,
                    LocalDateTime.now()
                )
        ).isPresent();
        assertThat(queueScore(redisKey, redisMember)).isNull();
    }

    @Test
    @DisplayName("대기 취소 후 재진입하면 DB와 Redis의 대기열 상태가 일치한다.")
    void cancelAndReenter_keepsQueueStateConsistent() {

        // given
        // 실제 Queue 진입 흐름으로 취소 가능한 DB 이력과 Redis 대기열을 함께 준비한다.
        String redisKey = "queue:game:" + game.getId();
        String redisMember = "user:" + user.getId();
        String queueKey = redisKey + ":" + redisMember;

        registerQueueEntry(Instant.now());

        // when
        // 취소 후 더 늦은 요청 시간으로 재진입해 기존 DB 이력과 Redis 순서를 갱신한다.
        queueService.cancelMyQueue(game.getId(), user.getId());

        QueueEntryHistoryStatus statusAfterCancel = findQueueEntryHistory(queueKey).getStatus();
        Double scoreAfterCancel = queueScore(redisKey, redisMember);

        Instant reentryRequestedAt = Instant.now().plusSeconds(1);
        QueueEntryRequestedEvent reentryEvent = queueEntryEvent(reentryRequestedAt);
        queueService.registerQueueEntry(reentryEvent);

        // then
        // 재진입은 기존 이력을 WAITING으로 되돌리고 Redis 대기열에 사용자를 다시 등록한다.
        assertThat(statusAfterCancel).isEqualTo(QueueEntryHistoryStatus.CANCELED);
        assertThat(scoreAfterCancel).isNull();

        QueueEntryHistory reenteredHistory = findQueueEntryHistory(queueKey);
        assertThat(reenteredHistory.getStatus()).isEqualTo(QueueEntryHistoryStatus.WAITING);
        assertThat(reenteredHistory.getCanceledAt()).isNull();

        assertThat(queueEntryHistoryRepository.count()).isEqualTo(1);
        assertThat(queueScore(redisKey, redisMember)).isEqualTo(reentryRequestedAt.toEpochMilli());
    }

    @Test
    @DisplayName("자동 입장으로 발급한 Queue-Token은 전체 21분과 좌석 탐색 3분의 만료시간을 가진다.")
    void admit_issuesTokenWithPolicyExpirationTimes() throws InterruptedException {

        // given
        // 실제 DB · Redis 대기열과 자동 입장 분산 락을 준비하여 운영 흐름과 같은 방식으로 Queue-Token을 발급한다.
        givenAdmitLockAcquired();
        registerQueueEntry(Instant.now());

        // when
        int admittedCount = admissionTokenService.admit(game.getId(), 1);

        // then
        // 한 명이 정상 입장되고 해당 사용자에게 ACTIVE Queue-Token이 저장됐는지 확인한다.
        assertThat(admittedCount).isEqualTo(1);

        AdmissionToken activeToken
            = admissionTokenRepository
                .findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(
                    game.getId(),
                    user.getId(),
                    AdmissionTokenStatus.ACTIVE,
                    LocalDateTime.now()
                )
                .orElseThrow();

        // 설정 상수가 아니라 DB에 저장된 발급시간을 기준으로 전체 21분과 최초 좌석 탐색 3분 정책을 함께 검증한다.
        assertThat(Duration.between(activeToken.getIssuedAt(), activeToken.getExpiresAt()))
            .isEqualTo(Duration.ofMinutes(21));
        assertThat(Duration.between(activeToken.getIssuedAt(), activeToken.getSeatBrowsingExpiresAt()))
            .isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    @DisplayName("전체 유효시간이 만료된 Queue-Token은 같은 대기열 진입 요청에서 정리되고 재진입할 수 있다.")
    void registerQueueEntry_withExpiredActiveToken_reentersQueue() {

        // given
        String redisKey = "queue:game:" + game.getId();
        String redisMember = "user:" + user.getId();
        String queueKey = redisKey + ":" + redisMember;
        String token = "qt_test";

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime issuedAt = now.minusMinutes(22);
        LocalDateTime expiresAt = issuedAt.plusMinutes(21);
        LocalDateTime seatBrowsingExpiresAt = issuedAt.plusMinutes(3);

        // ADMITTED 이력과 전체 유효시간이 지났지만 아직 ACTIVE인 Queue-Token을 준비한다.
        QueueEntryHistory queueEntryHistory = QueueEntryHistory.of(game, user, queueKey, issuedAt);
        queueEntryHistory.admit(now);
        queueEntryHistoryRepository.save(queueEntryHistory);
        Long savedQueueEntryHistoryId = queueEntryHistory.getId();

        AdmissionToken activeToken = AdmissionToken.of(game, user, token, issuedAt, expiresAt, seatBrowsingExpiresAt);
        admissionTokenRepository.save(activeToken);

        // 일반적인 취소 후 재진입 조건으로 통과하지 못하도록 재진입 요청시간을 만료 정리 시간보다 과거로 설정한다.
        Instant reentryRequestedAt = Instant.now().minusSeconds(1);
        QueueEntryRequestedEvent reentryEvent = queueEntryEvent(reentryRequestedAt);

        // when
        // 하나의 대기열 진입 요청에서 만료 토큰과 ADMITTED 이력을 정리한 뒤 같은 이력을 재진입시켜야 한다.
        queueService.registerQueueEntry(reentryEvent);

        // then
        // 전체 만료 토큰은 EXPIRED가 되고 기존 ADMITTED 이력은 새 이력 없이 WAITING으로 복구돼야 한다.
        assertThat(admissionTokenRepository.findByToken(token).orElseThrow().getStatus())
            .isEqualTo(AdmissionTokenStatus.EXPIRED);

        QueueEntryHistory reenteredHistory = findQueueEntryHistory(queueKey);
        assertThat(reenteredHistory.getStatus()).isEqualTo(QueueEntryHistoryStatus.WAITING);

        // 만료 정리에서는 기존 DB 이력을 재사용하고 새 이력을 만들지 않아야 한다.
        assertThat(reenteredHistory.getId()).isEqualTo(savedQueueEntryHistoryId);

        // 재진입한 이력에는 이전 입장 허용시간과 만료 정리 과정의 취소 시간이 남지 않아야 한다.
        assertThat(reenteredHistory.getAdmittedAt()).isNull();
        assertThat(reenteredHistory.getCanceledAt()).isNull();

        // 만료 정리에서는 새 토큰이나 DB 이력을 만들지 않고 기존 데이터를 한 건씩 유지해야 한다.
        assertThat(queueEntryHistoryRepository.count()).isEqualTo(1);
        assertThat(admissionTokenRepository.count()).isEqualTo(1);

        // Redis 대기열에는 재진입 요청시간을 점수로 등록해야 한다.
        assertThat(queueScore(redisKey, redisMember)).isEqualTo(reentryRequestedAt.toEpochMilli());
    }

    @Test
    @DisplayName("좌석 탐색 시간이 만료된 Queue-Token은 같은 대기열 진입 요청에서 정리되고 재진입할 수 있다.")
    void registerQueueEntry_withBrowsingExpiredActiveToken_reentersQueue() {

        // given
        String redisKey = "queue:game:" + game.getId();
        String redisMember = "user:" + user.getId();
        String queueKey = redisKey + ":" + redisMember;
        String token = "qt_test";

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime issuedAt = now.minusMinutes(4);
        LocalDateTime expiresAt = issuedAt.plusMinutes(21);
        LocalDateTime seatBrowsingExpiresAt = issuedAt.plusMinutes(3);

        // ADMITTED 이력과 전체 유효시간은 남았지만 좌석 탐색 시간이 지난 ACTIVE Queue-Token을 준비한다.
        QueueEntryHistory queueEntryHistory = QueueEntryHistory.of(game, user, queueKey, issuedAt);
        queueEntryHistory.admit(now);
        queueEntryHistoryRepository.save(queueEntryHistory);
        Long savedQueueEntryHistoryId = queueEntryHistory.getId();

        AdmissionToken activeToken = AdmissionToken.of(game, user, token, issuedAt, expiresAt, seatBrowsingExpiresAt);
        admissionTokenRepository.save(activeToken);

        // 일반적인 취소 후 재진입 조건으로 통과하지 못하도록 재진입 요청시간을 만료 정리 시간보다 과거로 설정한다.
        Instant reentryRequestedAt = Instant.now().minusSeconds(1);
        QueueEntryRequestedEvent reentryEvent = queueEntryEvent(reentryRequestedAt);

        // when
        // 하나의 대기열 진입 요청에서 탐색 만료 토큰과 ADMITTED 이력을 정리한 뒤 같은 이력을 재진입시켜야 한다.
        queueService.registerQueueEntry(reentryEvent);

        // then
        // 탐색 만료 토큰은 BROWSING_EXPIRED가 되고 기존 ADMITTED 이력은 새 이력 없이 WAITING으로 복구돼야 한다.
        assertThat(admissionTokenRepository.findByToken(token).orElseThrow().getStatus())
            .isEqualTo(AdmissionTokenStatus.BROWSING_EXPIRED);

        QueueEntryHistory reenteredHistory = findQueueEntryHistory(queueKey);
        assertThat(reenteredHistory.getStatus()).isEqualTo(QueueEntryHistoryStatus.WAITING);

        // 만료 정리에서는 기존 DB 이력을 재사용하고 새 이력을 만들지 않아야 한다.
        assertThat(reenteredHistory.getId()).isEqualTo(savedQueueEntryHistoryId);

        // 재진입한 이력에는 이전 입장 허용시간과 탐색 만료 정리 과정의 취소 시간이 남지 않아야 한다.
        assertThat(reenteredHistory.getAdmittedAt()).isNull();
        assertThat(reenteredHistory.getCanceledAt()).isNull();

        // 만료 정리에서는 새 토큰이나 DB 이력을 만들지 않고 기존 데이터를 한 건씩 유지해야 한다.
        assertThat(queueEntryHistoryRepository.count()).isEqualTo(1);
        assertThat(admissionTokenRepository.count()).isEqualTo(1);

        // Redis 대기열에는 재진입 요청시간을 점수로 등록해야 한다.
        assertThat(queueScore(redisKey, redisMember)).isEqualTo(reentryRequestedAt.toEpochMilli());
    }
}
