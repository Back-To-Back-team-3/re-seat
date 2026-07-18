package com.backtoback.reseat.domain.queue.service;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistory;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;
import com.backtoback.reseat.domain.queue.exception.QueueAdmissionFailedException;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.queue.repository.QueueEntryHistoryRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 대기열 통과 대상자에게 입장 토큰을 발급하는 서비스
 *
 * <p>경기별 분산 락으로 admission 중복 실행을 막고, DB 커밋 성공 후 Redis 대기열에서 입장 허용 사용자를 제거한다.</p>
 */
@Service
@RequiredArgsConstructor
public class AdmissionTokenService {

    private static final long TOKEN_TTL_MINUTES = 5L;
    private static final long ADMIT_LOCK_WAIT_SECONDS = 1L;
    private static final int MAX_ADMIT_LIMIT = 100;

    private final RedisTemplate<String, String> redisTemplate;
    private final RedissonClient redissonClient;
    private final AdmissionTokenRepository admissionTokenRepository;
    private final QueueEntryHistoryRepository queueEntryHistoryRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;

    /**
     * Redis 대기열 앞쪽 사용자들을 입장 허용 처리하고 입장 토큰을 발급한다.
     *
     * <p>동시에 같은 경기 admission이 실행되지 않도록 경기별 Redisson 락을 사용한다.</p>
     *
     * @param gameId 경기 ID
     * @param limit 입장 허용 처리할 최대 사용자 수
     * @return 실제 입장 허용 처리된 사용자 수
     */
    @Transactional
    public int admit(Long gameId, int limit) {

        if (limit <= 0) {
            return 0;
        }

        boolean lockReleaseRegistered = false;

        // 경기별 admission 동시 실행을 막아 같은 사용자가 중복 선발되지 않도록 한다.
        RLock lock = redissonClient.getLock(admitLockKey(gameId));
        boolean locked = false;

        try {
            locked = lock.tryLock(
                    ADMIT_LOCK_WAIT_SECONDS,
                    TimeUnit.SECONDS
            );

            if (!locked) {
                return 0;
            }

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            });

            lockReleaseRegistered = true;

            ZSetOperations<String, String> queueZSet = getZSetOperations();

            String redisKey = redisKey(gameId);

            // Redis ZSet 앞쪽 사용자부터 최대 safeLimit명 까지 입장 허용 대상으로 조회한다.
            int safeLimit = Math.min(limit, MAX_ADMIT_LIMIT);
            Set<String> members = queueZSet.range(redisKey, 0, safeLimit - 1);

            if (members == null || members.isEmpty()) {
                return 0;
            }

            Game game = gameRepository.findById(gameId)
                    .orElseThrow(() -> new GameNotFoundException(gameId));

            LocalDateTime issuedAt = LocalDateTime.now();
            LocalDateTime expiresAt = issuedAt.plusMinutes(TOKEN_TTL_MINUTES);

            int admittedCount = 0;

            for (String member: members) {
                Long userId = parseUserId(member);
                String queueKey = queueKey(gameId, userId);

                QueueEntryHistory queueEntryHistory = queueEntryHistoryRepository
                        .findByQueueKey(queueKey)
                        .orElse(null);

                if (queueEntryHistory == null || queueEntryHistory.getStatus() != QueueEntryHistoryStatus.WAITING) {
                    continue;
                }

                AdmissionToken activeToken = admissionTokenRepository
                        .findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(
                                gameId,
                                userId,
                                AdmissionTokenStatus.ACTIVE,
                                LocalDateTime.now()
                        )
                        .orElse(null);

                if (activeToken != null) {
                    queueEntryHistory.admit(activeToken.getIssuedAt());
                    continue;
                }

                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

                String token = createQueueToken();

                admissionTokenRepository.save(AdmissionToken.of(game, user, token, issuedAt, expiresAt));

                queueEntryHistory.admit(issuedAt);
                admittedCount++;
            }

            // DB 커밋이 성공한 뒤 입장 허용된 사용자를 Redis ZSet에서 제거한다.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    members.forEach(member -> queueZSet.remove(redisKey, member));
                }
            });

            return admittedCount;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new QueueAdmissionFailedException("입장 허용 처 중 스레드가 중단되었습니다.");
        } finally {
            if (locked && lock.isHeldByCurrentThread() && !lockReleaseRegistered) {
                lock.unlock();
            }
        }
    }

    private ZSetOperations<String, String> getZSetOperations() {
        return redisTemplate.opsForZSet();
    }

    // 경기별 대기열 Redis ZSet key: queue:game:{gameId}
    private String redisKey(Long gameId) {
        return "queue:game:" + gameId;
    }

    // 대기열 사용자: user:{userId}
    private String redisMember(Long userId) {
        return "user:" + userId;
    }

    // DB 이력 중복 방지 key: queue:game:{gameId}:user:{userId}
    private String queueKey(Long gameId, Long userId) {
        return redisKey(gameId) + ":" + redisMember(userId);
    }

    private Long parseUserId(String member) {
        try {
            return Long.parseLong(member.replace("user:", ""));
        } catch (NumberFormatException e) {
            throw new QueueAdmissionFailedException("Redis 대기열 사용자 정보가 올바르지 않습니다.");
        }
    }

    private String createQueueToken() {
        return "qt_" + UUID.randomUUID();
    }

    private String admitLockKey(Long gameId) {
        return "lock:queue:admit:" + gameId;
    }
}
