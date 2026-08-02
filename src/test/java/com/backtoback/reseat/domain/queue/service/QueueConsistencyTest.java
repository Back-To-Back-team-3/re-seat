package com.backtoback.reseat.domain.queue.service;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.dto.event.QueueEntryRequestedEvent;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Queue의 DB 대기 이력, Queue-Token과 Redis 대기열 간 정합성을 검증한다.
 */
@DisplayName("QueueConsistency")
public class QueueConsistencyTest extends BaseIntegrationTest {

    // test 프로파일에는 RedissonClient Bean이 없으므로 분산 락만 Mock으로 대체한다.
    @MockitoBean private RedissonClient redissonClient;

    @Autowired private RedisTemplate<String, String> redisTemplate;
    @Autowired private QueueEntryHistoryRepository queueEntryHistoryRepository;
    @Autowired private AdmissionTokenRepository admissionTokenRepository;

    @Autowired private GameRepository gameRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private StadiumRepository stadiumRepository;

    @Autowired private QueueService queueService;
    @Autowired private AdmissionTokenService admissionTokenService;

    private User user;
    private Game game;

    /**
     * 각 테스트 전 Queue 흐름에 필요한 Stadium, Team, User, Game을 저장한다.
     */
    @BeforeEach
    void setUp() {

        Stadium stadium = stadiumRepository.save(
                Stadium.of(
                        "테스트 구장",
                        "테스트시 테스트구",
                        10_000
                )
        );

        Team team = teamRepository.save(
                Team.of(
                        "테스트팀",
                        stadium
                )
        );

        user = userRepository.save(
                User.builder()
                        .email("test-user@test.com")
                        .password("test")
                        .name("테스트 사용자")
                        .phone("010-1234-5678")
                        .isVerified(true)
                        .role(UserRole.USER)
                        .status(UserStatus.ACTIVE)
                        .build()
        );

        game = gameRepository.save(
                Game.builder()
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
     * 요청 시간으로 현재 사용자와 경기의 대기열 진입 이벤트를 생성한다.
     *
     * @param requestedAt 대기열 진입 요청 시간
     * @return 대기열 진입 요청 이벤트
     */
    private QueueEntryRequestedEvent queueEntryEvent(Instant requestedAt) {

        return new QueueEntryRequestedEvent(
                UUID.randomUUID(),
                game.getId(),
                user.getId(),
                requestedAt
        );
    }

    /**
     * Queue-Key에 해당하는 DB 대기 이력을 조회한다.
     *
     * @param queueKey 조회할 대기 이력의 Queue-Key
     * @return 조회된 대기 이력
     */
    private QueueEntryHistory findQueueEntryHistory(String queueKey) {

        return queueEntryHistoryRepository
                .findByQueueKey(queueKey)
                .orElseThrow();
    }

    /**
     * Redis 대기열에서 사용자의 점수를 조회한다.
     *
     * @param redisKey 경기별 Redis 대기열 Key
     * @param redisMember 사용자 Redis Member
     * @return Redis 대기열 점수, 등록되지 않은 경우 null
     */
    private Double queueScore(String redisKey, String redisMember) {

        return redisTemplate
                .opsForZSet()
                .score(redisKey, redisMember);
    }

    @Test
    @DisplayName("자동 입장 후 DB와 Redis의 대기열 상태가 일치한다.")
    void admit_keepsQueueStateConsistent() throws InterruptedException {

        // given
        // 실제 Queue 진입 흐름으로 DB 이력과 Redis 대기열을 함께 준비한다.
        String lockKey = "lock:queue:admit:" + game.getId();

        RLock lock = mock(RLock.class);
        given(redissonClient.getLock(lockKey))
                .willReturn(lock);
        given(lock.tryLock(1L, TimeUnit.SECONDS))
                .willReturn(true);
        given(lock.isHeldByCurrentThread())
                .willReturn(true);

        String redisKey = "queue:game:" + game.getId();
        String redisMember = "user:" + user.getId();
        String queueKey = redisKey + ":" + redisMember;

        QueueEntryRequestedEvent event = queueEntryEvent(Instant.now());
        queueService.registerQueueEntry(event);

        // when
        int admittedCount = admissionTokenService.admit(game.getId(), 1);

        // then
        // 자동 입장은 DB 이력과 Queue-Token을 커밋한 뒤 Redis 대기열에서 사용자를 제거한다.
        assertThat(admittedCount)
                .isEqualTo(1);
        QueueEntryHistory admittedHistory = findQueueEntryHistory(queueKey);
        assertThat(admittedHistory.getStatus())
                .isEqualTo(QueueEntryHistoryStatus.ADMITTED);
        assertThat(admissionTokenRepository
                .findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(
                        game.getId(),
                        user.getId(),
                        AdmissionTokenStatus.ACTIVE,
                        LocalDateTime.now()))
                .isPresent();
        assertThat(queueScore(redisKey, redisMember))
                .isNull();
    }

    @Test
    @DisplayName("대기 취소 후 재진입하면 DB와 Redis의 대기열 상태가 일치한다.")
    void cancelAndReenter_keepsQueueStateConsistent() {

        // given
        // 실제 Queue 진입 흐름으로 취소 가능한 DB 이력과 Redis 대기열을 함께 준비한다.
        String redisKey = "queue:game:" + game.getId();
        String redisMember = "user:" + user.getId();
        String queueKey = redisKey + ":" + redisMember;

        QueueEntryRequestedEvent event = queueEntryEvent(Instant.now());
        queueService.registerQueueEntry(event);

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
        assertThat(statusAfterCancel)
                .isEqualTo(QueueEntryHistoryStatus.CANCELED);
        assertThat(scoreAfterCancel)
                .isNull();

        QueueEntryHistory reenteredHistory = findQueueEntryHistory(queueKey);
        assertThat(reenteredHistory.getStatus())
                .isEqualTo(QueueEntryHistoryStatus.WAITING);
        assertThat(reenteredHistory.getCanceledAt())
                .isNull();

        assertThat(queueEntryHistoryRepository.count())
                .isEqualTo(1);
        assertThat(queueScore(redisKey, redisMember))
                .isEqualTo(reentryRequestedAt.toEpochMilli());
    }
}
