package com.backtoback.reseat.domain.queue.service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistory;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;
import com.backtoback.reseat.domain.queue.exception.QueueAdmissionFailedException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenExpiredException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenInvalidException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenRequiredException;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.queue.repository.QueueEntryHistoryRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 대기열 통과 대상자에게 입장 토큰을 발급하는 서비스
 *
 * <p>경기별 분산 락으로 중복 실행을 막고, DB 커밋 성공 후 이번 처리 대상으로 조회한 사용자를 Redis 대기열에서 제거한다.</p>
 */
@Service
@RequiredArgsConstructor
public class AdmissionTokenService {

    // 입장 허용 후 발급되는 Queue-Token의 유효 시간
    private static final long TOKEN_TTL_MINUTES = 5L;

    // 동일 경기의 입장 처리 락을 획득하기 위해 기다리 최대 시간
    private static final long ADMIT_LOCK_WAIT_SECONDS = 1L;

    // 한 번의 입장 처리에서 조회할 수 있는 최대 사용자 수
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
     * <p>경기별 Redisson 분산 락으로 입장 처리를 순서대로 실행하고,
     * 대기 이력은 비관적 락으로 조회하여 취소와의 상태 변경 충돌을 막는다.</p>
     *
     * @param gameId 경기 ID
     * @param limit  입장 허용 처리할 최대 사용자 수
     * @return 새로운 입장 토큰이 발급된 사용자 수
     */
    @Transactional
    public int admit(Long gameId, int limit) {

        // 처리 개수가 0이하면 Redis와 DB에 접근하지 않고 종료한다.
        if (limit <= 0) {
            return 0;
        }

        boolean lockReleaseRegistered = false;

        // 같은 경기의 입장 처리가 동시에 실행되면 동일 사용자가 중복 선발될 수 있으므로 경기별 분산락을 사용한다.
        RLock lock = redissonClient.getLock(admitLockKey(gameId));
        boolean locked = false;

        try {
            // 지정된 시간 동안만 락 획득을 기다리고 획득하지 못하면 이번 입장 처리를 건너뛴다.
            // leaseTime을 지정하지 않았으므로 작업 중에는 Redisson Watchdog이 락 만료 시간을 자동으로 연장한다.
            locked = lock.tryLock(
                ADMIT_LOCK_WAIT_SECONDS,
                TimeUnit.SECONDS
            );

            if (!locked) {
                return 0;
            }

            // DB 트랜잭션이 끝나기 전에 락을 해제하면 다른 작업이 같은 사용자를 조회할 수 있다.
            // 커밋 또는 롤백이 완전히 끝난 후 현재 스레드가 소유한 락을 해제한다.
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

            // 과도한 일괄 처리를 막기 위해 요청된 limit을 최대 허용 범위로 제한한다.
            // Redis ZSet의 점수가 낮은 사용자부터 safeLimit명까지 이번 처리 대상으로 조회한다.
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

            for (String member : members) {
                Long userId = parseUserId(member);
                String queueKey = queueKey(gameId, userId);

                // 대기 취소와 입장 허용이 동시에 변경하지 않도록 대기 이력을 비관적 락으로 조회한다.
                QueueEntryHistory queueEntryHistory = queueEntryHistoryRepository
                    .findByQueueKeyWithPessimisticWriteLock(queueKey)
                    .orElse(null);

                // Redis에는 존재하지만 DB 이력이 없거나 이미 대기 상태가 끝난 사용자는 새 토큰을 발행하지 않는다.
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

                // 이전 처리에서 활성 토큰은 발급됐지만 DB 대기 이력이 WAITING으로 남은 경우 기존 토큰을 재사용한다.
                // 토큰을 중복 발급하지 않고 기존 발급 시간을 기준으로 대기 이력만 ADMITTED 상태로 복구한다.
                if (activeToken != null) {
                    queueEntryHistory.admit(activeToken.getIssuedAt());
                    continue;
                }

                User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

                // 활성 토큰이 없는 정상 대기 사용자에게만 새로운 Queue-Token을 발급한다.
                String token = createQueueToken();

                // 입장 토큰 저장과 대기 이력 상태 변경은 같은 DB 트랜잭션 안에서 처리한다.
                admissionTokenRepository.save(AdmissionToken.of(game, user, token, issuedAt, expiresAt));

                queueEntryHistory.admit(issuedAt);
                admittedCount++;
            }

            // DB 커밋이 성공한 경우 이번에 조회한 Redis 사용자들을 모두 제거한다.
            // 새 토큰 발급 사용자 뿐만 아니라 기존 활성 토큰 복구 사용자와 오래 남은 Redis 데이터도 함께 정리한다.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    members.forEach(member -> queueZSet.remove(redisKey, member));
                }
            });

            return admittedCount;
        } catch (InterruptedException e) {
            // InterruptedException을 처리하면서 제거된 interrupt 상태를 복원해 상위 실행 흐름에서도 중단 사실을 확인할 수 있게 한다.
            Thread.currentThread().interrupt();
            throw new QueueAdmissionFailedException("입장 허용 처리 중 스레드가 중단되었습니다.");
        } finally {
            // 트랜잭션 동기화 등록 전에 예외가 발생한 경우 afterCompletion이 실행되지 않으므로 여기서 직접 lock을 해제한다.
            if (locked && lock.isHeldByCurrentThread() && !lockReleaseRegistered) {
                lock.unlock();
            }
        }
    }

    /**
     * Queue-Token이 요청한 사용자와 경기에 속하며 현재 사용할 수 있는지 검증한다.
     *
     * @param userId 요청한 사용자 ID
     * @param gameId 입장하려는 경기 ID
     * @param token  검증할 Queue-Token 값
     */
    @Transactional(noRollbackFor = QueueTokenExpiredException.class)
    public void validateToken(Long userId, Long gameId, String token) {

        LocalDateTime now = LocalDateTime.now();

        validateRequiredToken(token);

        AdmissionToken admissionToken = admissionTokenRepository.findByToken(token)
            .orElseThrow(QueueTokenInvalidException::new);

        validateTokenContext(admissionToken, userId, gameId);

        expireIfNeeded(admissionToken, now);

        admissionToken.validateUsableAt(now);
    }

    /**
     * Queue-Token을 비관적 쓰기 잠금으로 조회하고 사용 완료 상태로 전환한다.
     *
     * @param userId 요청한 사용자 ID
     * @param gameId 입장하려는 경기 ID
     * @param token  소비할 Queue-Token 값
     */
    @Transactional(noRollbackFor = QueueTokenExpiredException.class)
    public void consumeToken(Long userId, Long gameId, String token) {

        LocalDateTime now = LocalDateTime.now();

        validateRequiredToken(token);

        AdmissionToken admissionToken = admissionTokenRepository.findByTokenWithPessimisticWriteLock(token)
            .orElseThrow(QueueTokenInvalidException::new);

        validateTokenContext(admissionToken, userId, gameId);

        expireIfNeeded(admissionToken, now);

        admissionToken.use(now);
    }

    /**
     * 사용 완료된 Queue-Token을 비관적 락으로 조회하고 활성 상태로 되돌린다.
     *
     * <p>사용자와 경기 정보를 검증하고,
     * 기존 발급 시간과 만료 시간을 유지한채 동일한 토큰을 재활성화 한다.</p>
     *
     * @param userId 요청한 사용자 ID
     * @param gameId 입장하려는 경기 ID
     * @param token  재활성화할 Queue-Token 값
     */
    @Transactional
    public void reactivateToken(Long userId, Long gameId, String token) {

        LocalDateTime now = LocalDateTime.now();

        validateRequiredToken(token);

        AdmissionToken admissionToken = admissionTokenRepository.findByTokenWithPessimisticWriteLock(token)
            .orElseThrow(QueueTokenInvalidException::new);

        validateTokenContext(admissionToken, userId, gameId);

        admissionToken.reactivate(now);
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

    // Redis 대기열 구성원 값에서 사용자 ID를 추출한다.
    private Long parseUserId(String member) {
        try {
            return Long.parseLong(member.replace("user:", ""));
        } catch (NumberFormatException e) {
            throw new QueueAdmissionFailedException("Redis 대기열 사용자 정보가 올바르지 않습니다.");
        }
    }

    // 입장 허용 사용자에게 발급할 Queue-Token을 생성한다.
    private String createQueueToken() {
        return "qt_" + UUID.randomUUID();
    }

    // 경기별 입장 처리 분산 락 Key를 생성한다.
    private String admitLockKey(Long gameId) {
        return "lock:queue:admit:" + gameId;
    }

    // Queue-Token 값이 누락되거나 공백인지 검증한다.
    private void validateRequiredToken(String token) {
        if (token == null || token.isBlank()) {
            throw new QueueTokenRequiredException();
        }
    }

    // 입장 토큰의 사용자와 경기가 요청 정보와 일치하는지 검증한다.
    private void validateTokenContext(AdmissionToken admissionToken, Long userId, Long gameId) {
        if (!admissionToken.getUser().getId().equals(userId)
            || !admissionToken.getGame().getId().equals(gameId)) {
            throw new QueueTokenInvalidException();
        }
    }

    // 만료 시간이 지난 ACTIVE 토큰을 EXPIRED 상태로 전환하고 만료 예외를 발생시킨다.
    private void expireIfNeeded(AdmissionToken admissionToken, LocalDateTime now) {
        if (admissionToken.getStatus() == AdmissionTokenStatus.ACTIVE && admissionToken.isExpiredAt(now)) {
            admissionToken.expire(now);
            throw new QueueTokenExpiredException();
        }
    }
}
