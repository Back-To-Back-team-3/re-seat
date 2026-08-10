package com.backtoback.reseat.domain.queue.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import com.backtoback.reseat.domain.queue.exception.QueueInvalidStatusException;
import com.backtoback.reseat.domain.queue.exception.QueueRegistrationFailedException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenRequiredException;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.queue.repository.QueueEntryHistoryRepository;
import com.backtoback.reseat.domain.queue.repository.QueueUserRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

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
    private final QueueUserRepository queueUserRepository;
    private final QueueEntryEventPublisher queueEntryEventPublisher;

    /**
     * 사용자의 현재 대기열 상태를 조회한다.
     *
     * <p>활성 입장 토큰이 있으면 ADMITTED 상태로 반환하고, 없으면 Redis ZSet 순번을 조회한다.</p>
     *
     * <p>대기 중인 사용자는 현재 순번과 자동 입장 정책을 기준으로 예상 대기시간을 함께 계산한다.</p>
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

        // Redis 순번은 0부터 시작하므로 사용자에게 반환할 순번은 1을 더해 계산한다.
        Long userRank = redisRank + 1;

        // 현재 순번이 포함되는 자동 입장 처리 횟수를 기준으로 예상 대기시간을 계산한다.
        Long estimatedWaitSeconds = QueueAdmissionPolicy.calculateEstimatedWaitSeconds(userRank);

        return QueueStatusResponse.builder()
            .rank(userRank)
            .estimatedWaitSeconds(estimatedWaitSeconds)
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
     * 사용자의 경기별 대기열 진입과 발급된 활성 입장 토큰을 취소한다.
     *
     * <p>사용자, 대기 이력, 입장 토큰을 순서대로 잠가
     * 입장 허용 및 토큰 소비와의 상태 변경 충돌을 막는다.</p>
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

        // 대기 취소와 다른 경기 진입이 동시에 처리되지 않도록 사용자 행을 잠근다.
        queueUserRepository
            .findByIdWithPessimisticWriteLock(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        // 대기 취소와 입장 허용이 동시에 상태를 변경하지 않도록 대기 이력을 비관적 락으로 조회한다.
        QueueEntryHistory queueEntryHistory = queueEntryHistoryRepository
            .findByQueueKeyWithPessimisticWriteLock(queueKey)
            .orElseThrow(QueueEntryNotFoundException::new);

        // 토큰 소비와 취소가 동시에 상태를 변경하지 않도록 활성 입장 토큰을 비관적 락으로 조회한다.
        AdmissionToken activeToken = admissionTokenRepository
            .findByGame_IdAndUser_IdAndStatusWithPessimisticWriteLock(
                gameId,
                userId,
                AdmissionTokenStatus.ACTIVE)
            .orElse(null);

        // ADMITTED 이력은 함께 회수할 ACTIVE 토큰이 있을 때만 취소한다.
        if (queueEntryHistory.getStatus() == QueueEntryHistoryStatus.ADMITTED &&
            Objects.isNull(activeToken)) {
            throw new QueueInvalidStatusException("입장 허용 상태는 활성 입장 토큰과 함께 취소해야 합니다.");
        }

        // 대기 이력을 CANCELED로 변경하고 활성 입장 토큰이 있으면 함께 REVOKED로 전환한다.
        LocalDateTime now = LocalDateTime.now();
        queueEntryHistory.cancel(now);

        if (activeToken != null) {
            activeToken.revoke();
        }

        // DB 트랜잭션이 롤백되면 Redis 대기열을 그대로 유지해야 한다.
        // DB 커밋이 완료된 경우에만 Redis에서 사용자를 제거하여 두 저장소의 상태 불일치를 방지한다.
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

    /**
     * 경기와 사용자의 존재 여부를 확인하고 Kafka로 대기열 진입 이벤트를 발행한다.
     *
     * @param gameId 대기열에 진입할 경기 ID
     * @param userId 대기열 진입을 요청한 사용자 ID
     * @return Kafka 이벤트 발행 결과를 나타내는 비동기 작업
     */
    @Transactional(readOnly = true)
    public CompletableFuture<Void> requestQueueEntry(Long gameId, Long userId) {

        // 처리할 수 없는 이벤트가 Kafka에 발행되지 않도록 경기와 사용자의 존재 여부를 먼저 확인한다.
        if (!gameRepository.existsById(gameId)) {
            throw new GameNotFoundException(gameId);
        }

        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("사용자를 찾을 수 없습니다.");
        }

        // eventId는 이벤트 로그 추적에 사용하고, requestAt은 Redis ZSet의 대기 순서를 결정하는 기준으로 사용한다.
        QueueEntryRequestedEvent event = new QueueEntryRequestedEvent(
            UUID.randomUUID(),
            gameId,
            userId,
            Instant.now());

        // Kafka 발행 실패는 대기열 진입 요청 실패로 변환하여 비동기 작업을 예외 상태로 완료한다.
        return queueEntryEventPublisher
            .publish(event)
            .thenAccept(result -> {})
            .exceptionally(exception -> {
                throw new QueueEventPublishFailedException(exception);
            });
    }

    /**
     * kafka 대기열 진입 이벤트를 바탕으로 DB 대기 이력과 Redis 대기열을 등록한다.
     *
     * <p>사용자 행에 비관적 락을 적용하여 동일 사용자의 여러 경기 대기열 등록을 순서대로 처리한다.</p>
     *
     * @param event 대기열 진입 요청 이벤트
     */
    @Transactional
    public void registerQueueEntry(QueueEntryRequestedEvent event) {

        // 잘못된 이벤트는 재시도해도 처리할 수 없으므로 DB와 Redis에 접근하기 전에 검증한다.
        validateQueueEntryEvent(event);

        Game game = gameRepository.findById(event.gameId())
            .orElseThrow(() -> new GameNotFoundException(event.gameId()));

        // 동일 사용자의 여러 경기 대기열 등록이 동시에 처리되지 않도록 사용자 행을 잠근다.
        User user = queueUserRepository.findByIdWithPessimisticWriteLock(event.userId())
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        LocalDateTime now = LocalDateTime.now();

        boolean hasActiveToken = admissionTokenRepository
            .existsByUser_IdAndStatusAndExpiresAtAfter(
                user.getId(),
                AdmissionTokenStatus.ACTIVE,
                now);
        boolean hasWaitingEntryInAnotherGame = queueEntryHistoryRepository
            .existsByUser_IdAndGame_IdNotAndStatus(
                user.getId(),
                game.getId(),
                QueueEntryHistoryStatus.WAITING);

        // 다른 경기에서 대기 중이거나 유효한 입장 토큰이 있다면 새 대기열에 등록하지 않는다.
        if (hasActiveToken || hasWaitingEntryInAnotherGame) {
            return;
        }

        ZSetOperations<String, String> queueZSet = getZSetOperations();
        LocalDateTime requestedAt = event.requestedAt().atZone(ZoneId.systemDefault()).toLocalDateTime();

        String redisKey = redisKey(event.gameId());
        String redisMember = redisMember(event.userId());
        String queueKey = queueKey(event.gameId(), event.userId());
        long score = event.requestedAt().toEpochMilli();

        QueueEntryHistory existingHistory = queueEntryHistoryRepository
            .findByQueueKey(queueKey)
            .orElse(null);

        // 동일 경기와 사용자의 DB 이력이 있으면 새로운 이력을 중복 생성하지 않는다.
        // 기존 상태가 WAITING이면 Consumer 재처리 과정에서 누락됐을 수 있는 Redis 대기열 정보만 복구한다.
        if (Objects.nonNull(existingHistory)) {
            if (existingHistory.getStatus() == QueueEntryHistoryStatus.WAITING) {
                addRedisQueueEntryIfAbsent(queueZSet, redisKey, redisMember, score);
            }

            // 취소된 이력은 새 요청 시간으로 갱신하고 DB 커밋 후 Redis 점수도 덮어써 대기열 맨 뒤에 등록한다.
            if (existingHistory.getStatus() == QueueEntryHistoryStatus.CANCELED) {
                // 취소 시간보다 늦게 발행된 새로운 요청만 재진입으로 처리한다.
                if (existingHistory.getCanceledAt().isBefore(requestedAt)) {
                    existingHistory.reenter(requestedAt);
                    // DB 커밋이 완료된 경우에만 Redis 대기 순서를 갱신하여 두 저장소의 상태 불일치를 방지한다.
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            addOrUpdateRedisQueueEntry(queueZSet, redisKey, redisMember, score);
                        }
                    });
                }
            }

            return;
        }

        // Redis에 등록하기 전에 DB 이력을 즉시 반영하여 queueKey 중복 여부를 먼저 확인한다.
        queueEntryHistoryRepository.saveAndFlush(
            QueueEntryHistory.of(
                game, user, queueKey, now));

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

    /**
     * 대기열 진입 이벤트의 필수 값과 경기 · 사용자 ID를 검증한다.
     *
     * @param event 검증할 대기열 진입 요청 이벤트
     */
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

        if (Objects.isNull(event.userId()) || event.userId() <= 0) {
            throw new QueueInvalidEventException("대기열 진입 이벤트의 사용자 ID가 올바르지 않습니다.");
        }

        if (Objects.isNull(event.requestedAt())) {
            throw new QueueInvalidEventException("대기열 진입 요청 시간이 누락되었습니다.");
        }
    }

    /**
     * Redis 대기열에 등록되지 않은 사용자만 요청 시간 점수로 추가한다.
     *
     * @param queueZSet   Redis ZSet 연산 객체
     * @param redisKey    경기별 Redis 대기열 Key
     * @param redisMember 대기열 사용자 member
     * @param score       대기 순서를 결정하는 요청 시간 점수
     */
    private void addRedisQueueEntryIfAbsent(
        ZSetOperations<String, String> queueZSet,
        String redisKey,
        String redisMember,
        long score) {

        // 이미 등록된 사용자는 기존 점수를 유지하고 등록되지 않은 사용자만 전달받은 요청 시간으로 추가한다.
        Boolean registered = queueZSet.addIfAbsent(redisKey, redisMember, score);

        // 결과 자체가 반환되지 않은 경우에만 등록 실패로 처리한다.
        if (Objects.isNull(registered)) {
            throw new QueueRegistrationFailedException();
        }
    }

    /**
     * Redis 대기열에 사용자를 추가하거나 요청 시간 점수로 대기 순서를 갱신한다.
     *
     * @param queueZSet   Redis ZSet 연산 객체
     * @param redisKey    경기별 Redis 대기열 Key
     * @param redisMember 대기열 사용자 member
     * @param score       대기 순서를 결정하는 요청 시간 점수
     */
    private void addOrUpdateRedisQueueEntry(
        ZSetOperations<String, String> queueZSet,
        String redisKey,
        String redisMember,
        long score) {

        // 등록 여부와 관계없이 새 요청 시간으로 점수를 반영해 대기 순서를 갱신한다.
        Boolean registered = queueZSet.add(redisKey, redisMember, score);

        // 결과 자체가 반환되지 않은 경우에만 등록 실패로 처리한다.
        if (Objects.isNull(registered)) {
            throw new QueueRegistrationFailedException();
        }
    }
}
