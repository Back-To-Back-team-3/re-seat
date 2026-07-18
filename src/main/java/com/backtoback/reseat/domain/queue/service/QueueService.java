package com.backtoback.reseat.domain.queue.service;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.GameNotFoundException;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.dto.event.QueueEntryRequestedEvent;
import com.backtoback.reseat.domain.queue.dto.response.AdmitEventResponse;
import com.backtoback.reseat.domain.queue.dto.response.QueueCancelResponse;
import com.backtoback.reseat.domain.queue.dto.response.QueueStatusResponse;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistory;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;
import com.backtoback.reseat.domain.queue.exception.QueueEntryNotFoundException;
import com.backtoback.reseat.domain.queue.exception.QueueEventPublishFailedException;
import com.backtoback.reseat.domain.queue.exception.QueueInvalidEventException;
import com.backtoback.reseat.domain.queue.exception.QueueRegistrationFailedException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenRequiredException;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.queue.repository.QueueEntryHistoryRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 대기열 진입, 상태 조회, 취소, 입장 허용 이벤트 조회를 담당하는 서비스
 *
 * <p>Redis ZSet으로 실시간 순번을 관리하고, DB에는 대기열 진입 이력과 상태를 저장한다.</p>
 */
@Service
@RequiredArgsConstructor
public class QueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final QueueEntryHistoryRepository queueEntryHistoryRepository;
    private final AdmissionTokenRepository admissionTokenRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final QueueEntryEventPublisher queueEntryEventPublisher;

    /**
     * 사용자의 현재 대기열 상태를 조회한다.
     *
     * <p>활성 입장 토큰이 있으면 ADMITTED 상태로 반환하고, 없으면 Redis ZSet 순번을 조회한다.</p>
     *
     * @param gameId 경기 ID
     * @param userId 사용자 ID
     * @return 현재 대기 상태 응답
     */
    @Transactional(readOnly = true)
    public QueueStatusResponse getMyQueueStatus(Long gameId, Long userId) {

        ZSetOperations<String, String> queueZSet = getZSetOperations();

        // 활성 입장 토큰이 있으면 입장 허용 상태를 반환한다.
        AdmissionToken activeToken = admissionTokenRepository
                .findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(
                        gameId,
                        userId,
                        AdmissionTokenStatus.ACTIVE,
                        LocalDateTime.now())
                .orElse(null);

        if (Objects.nonNull(activeToken)) {
            return QueueStatusResponse.builder()
                    .rank(0L)
                    .estimatedWaitSeconds(0L)
                    .queueStatus(QueueEntryHistoryStatus.ADMITTED)
                    .admitted(true)
                    .build();
        }

        String redisKey = redisKey(gameId);
        String redisMember = redisMember(userId);
        Long redisRank = queueZSet.rank(redisKey, redisMember);

        // Redis에 없으면 현재 대기열에 등록된 사용자가 아님
        if (Objects.isNull(redisRank)) {
            throw new QueueEntryNotFoundException();
        }

        return QueueStatusResponse.builder()
                    .rank(redisRank + 1)
                    .estimatedWaitSeconds(null)
                    .queueStatus(QueueEntryHistoryStatus.WAITING)
                    .admitted(false)
                    .build();
    }

    /**
     * SSE admit 이벤트에 전달할 입장 토큰 정보를 조회한다.
     *
     * @param gameId 경기 ID
     * @param userId 사용자 ID
     * @return 입장 허용 이벤트 응답
     */
    @Transactional(readOnly = true)
    public AdmitEventResponse getAdmitEvent(Long gameId, Long userId) {

        // 활성 입장 토큰을 조회해 SSE admit 이벤트 응답을 만든다.
        AdmissionToken activeToken = admissionTokenRepository
                .findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(
                        gameId,
                        userId,
                        AdmissionTokenStatus.ACTIVE,
                        LocalDateTime.now())
                .orElseThrow(QueueTokenRequiredException::new);

        return AdmitEventResponse.builder()
                .admitted(true)
                .queueToken(activeToken.getToken())
                .tokenExpiresAt(activeToken.getExpiresAt())
                .build();
    }

    /**
     * 사용자의 경기별 대기열 진입을 취소한다.
     *
     * <p>DB 이력이 취소 가능한 상태인지 확인 후 Redis 대기열에서 제거한다.</p>
     *
     * @param gameId 경기 ID
     * @param userId 사용자 ID
     * @return 대기열 취소 응답
     */
    @Transactional
    public QueueCancelResponse cancelMyQueue(Long gameId, Long userId) {

        ZSetOperations<String, String> queueZSet = getZSetOperations();

        String redisKey = redisKey(gameId);
        String redisMember = redisMember(userId);
        String queueKey = queueKey(gameId, userId);

        // DB 상태를 CANCELED로 전이한 뒤 Redis 대기열에서 제거한다.
        QueueEntryHistory queueEntryHistory =
                queueEntryHistoryRepository.findByQueueKey(queueKey)
                .orElseThrow(QueueEntryNotFoundException::new);

        queueEntryHistory.cancel(LocalDateTime.now());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                queueZSet.remove(redisKey, redisMember);
            }
        });

        return QueueCancelResponse.builder()
                .gameId(gameId)
                .queueStatus(QueueEntryHistoryStatus.CANCELED)
                .build();
    }

    @Transactional(readOnly = true)
    public CompletableFuture<Void> requestQueueEntry(Long gameId, Long userId) {

        if (!gameRepository.existsById(gameId)) {
            throw new GameNotFoundException(gameId);
        }

        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("사용자를 찾을 수 없습니다.");
        }

        QueueEntryRequestedEvent event = new QueueEntryRequestedEvent(
                UUID.randomUUID(),
                gameId,
                userId,
                Instant.now()
        );

        return queueEntryEventPublisher
                .publish(event)
                .thenAccept(result -> {})
                .exceptionally(exception -> {
                    throw new QueueEventPublishFailedException();
                });
    }

    /**
     * kafka 대기열 진입 이벤트를 사용하여 Redis 대기열과 DB 이력을 등록한다.
     *
     * @param event 대기열 진입 요청 이벤트
     */
    @Transactional
    public void registerQueueEntry(QueueEntryRequestedEvent event) {

        validateQueueEntryEvent(event);

        Game game = gameRepository.findById(event.gameId())
                .orElseThrow(() -> new GameNotFoundException(event.gameId()));
        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        boolean hasActiveToken = admissionTokenRepository
                .findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(
                        event.gameId(),
                        event.userId(),
                        AdmissionTokenStatus.ACTIVE,
                        LocalDateTime.now()
                )
                .isPresent();

        // 이미 입장 토큰이 발급된 사용자는 다시 대기열에 등록하지 않는다.
        if (hasActiveToken) {
            return;
        }

        ZSetOperations<String, String> queueZSet = getZSetOperations();

        String redisKey = redisKey(event.gameId());
        String redisMember = redisMember(event.userId());
        String queueKey = queueKey(event.gameId(), event.userId());
        long score = event.requestedAt().toEpochMilli();

        QueueEntryHistory existingHistory = queueEntryHistoryRepository
                .findByQueueKey(queueKey)
                .orElse(null);

        if (Objects.nonNull(existingHistory)) {
            // Kafka 재전달 시 기존 대기 상태의 Redis 정보만 복구한다.
            if (existingHistory.getStatus() == QueueEntryHistoryStatus.WAITING) {
                addRedisQueueEntryIfAbsent(queueZSet, redisKey, redisMember, score);
            }

            return;
        }

        queueEntryHistoryRepository.saveAndFlush(
                QueueEntryHistory.of(
                        game, user, queueKey, LocalDateTime.now()
                )
        );

        addRedisQueueEntryIfAbsent(queueZSet, redisKey, redisMember, score);
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

    private void validateQueueEntryEvent(QueueEntryRequestedEvent event) {

        if (Objects.isNull(event)) {
            throw new QueueInvalidEventException("대기열 진입 이벤트가 비어 있습니다.");
        }

        if (Objects.isNull(event.eventId())) {
            throw new QueueInvalidEventException("대기열 진입 이벤트 ID가 누락되었습니다.");
        }

        if (Objects.isNull(event.gameId()) || event.gameId() <= 0) {
            throw new QueueInvalidEventException("대기열 진입 이벤트의 경기 ID가 올바르지 않습니다.");
        }

        if(Objects.isNull(event.userId()) || event.userId() <= 0) {
            throw new QueueInvalidEventException("대기열 진입 이벤트의 사용자 ID가 올바르지 않습니다.");
        }

        if(Objects.isNull(event.requestedAt())) {
            throw new QueueInvalidEventException("대기열 진입 요청 시간이 누락되었습니다.");
        }
    }

    private void addRedisQueueEntryIfAbsent(
            ZSetOperations<String, String> queueZSet,
            String redisKey,
            String redisMember,
            long score
    ) {

        Boolean registered = queueZSet.addIfAbsent(redisKey, redisMember, score);

        if (Objects.isNull(registered)) {
            throw new QueueRegistrationFailedException();
        }
    }
}
