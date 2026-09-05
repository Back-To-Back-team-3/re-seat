package com.backtoback.reseat.domain.queue.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.exception.BookingNotOpenException;
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
import com.backtoback.reseat.domain.queue.entity.QueueEntryRejectionReason;
import com.backtoback.reseat.domain.queue.exception.QueueEntryCancellationTokenRequiredException;
import com.backtoback.reseat.domain.queue.exception.QueueEntryEventGameIdInvalidException;
import com.backtoback.reseat.domain.queue.exception.QueueEntryEventIdRequiredException;
import com.backtoback.reseat.domain.queue.exception.QueueEntryEventRequestedAtRequiredException;
import com.backtoback.reseat.domain.queue.exception.QueueEntryEventRequiredException;
import com.backtoback.reseat.domain.queue.exception.QueueEntryEventUserIdInvalidException;
import com.backtoback.reseat.domain.queue.exception.QueueEntryNotFoundException;
import com.backtoback.reseat.domain.queue.exception.QueueEventPublishFailedException;
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
    private final QueueEntryRejectionService queueEntryRejectionService;

    /**
     * 사용자의 현재 대기열 상태를 조회한다.
     * <p>활성 입장 토큰이 있으면 ADMITTED 상태로 반환하고, 없으면 Redis ZSet 순번을 조회한다.</p>
     * <p>대기 중인 사용자는 현재 순번과 자동 입장 정책을 기준으로 예상 대기시간을 함께 계산한다.</p>
     *
     * @param gameId 경기 ID
     * @param userId 사용자 ID
     * @return 현재 대기 상태 응답
     */
    @Transactional(readOnly = true)
    public QueueStatusResponse getMyQueueStatus(Long gameId, Long userId) {

        ZSetOperations<String, String> queueZSet = getZSetOperations();

        LocalDateTime now = LocalDateTime.now();

        // 사용 가능한 활성 입장 토큰이 있으면 입장 허용 상태를 반환한다.
        AdmissionToken activeToken
            = admissionTokenRepository
                .findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(gameId, userId, AdmissionTokenStatus.ACTIVE, now)
                .filter(token -> !token.isSeatBrowsingExpiredAt(now))
                .orElse(null);

        if (Objects.nonNull(activeToken)) {
            return QueueStatusResponse
                .builder()
                .rank(0L)
                .estimatedWaitSeconds(0L)
                .queueStatus(QueueEntryHistoryStatus.ADMITTED)
                .admitted(true)
                .build();
        }

        String waitingQueueRedisKey = waitingQueueRedisKey(gameId);
        String redisMember = redisMember(userId);
        Long redisRank = queueZSet.rank(waitingQueueRedisKey, redisMember);

        // Redis에 없으면 현재 대기열에 등록된 사용자가 아님
        if (Objects.isNull(redisRank)) {
            throw new QueueEntryNotFoundException();
        }

        // Redis 순번은 0부터 시작하므로 사용자에게 반환할 순번은 1을 더해 계산한다.
        long userRank = redisRank + 1;

        // 현재 순번이 포함되는 자동 입장 처리 횟수를 기준으로 예상 대기시간을 계산한다.
        Long estimatedWaitSeconds = QueueAdmissionPolicy.calculateEstimatedWaitSeconds(userRank);

        return QueueStatusResponse
            .builder()
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

        LocalDateTime now = LocalDateTime.now();

        // 사용 가능한 활성 입장 토큰을 조회해 SSE admit 이벤트 응답을 만든다.
        AdmissionToken activeToken
            = admissionTokenRepository
                .findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(gameId, userId, AdmissionTokenStatus.ACTIVE, now)
                .filter(token -> !token.isSeatBrowsingExpiredAt(now))
                .orElseThrow(QueueTokenRequiredException::new);

        return AdmitEventResponse
            .builder()
            .admitted(true)
            .queueToken(activeToken.getToken())
            .tokenExpiresAt(activeToken.getExpiresAt())
            .tokenSeatBrowsingExpiresAt(activeToken.getSeatBrowsingExpiresAt())
            .build();
    }

    /**
     * 사용자의 경기별 대기열 진입과 발급된 활성 입장 토큰을 취소한다.
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

        String waitingQueueRedisKey = waitingQueueRedisKey(gameId);
        String redisMember = redisMember(userId);
        String queueEntryKey = queueEntryKey(gameId, userId);

        // 대기 취소와 다른 경기 진입이 동시에 처리되지 않도록 사용자 행을 잠근다.
        queueUserRepository.findByIdWithPessimisticWriteLock(userId).orElseThrow(UserNotFoundException::new);

        // 대기 취소와 입장 허용이 동시에 상태를 변경하지 않도록 대기 이력을 비관적 락으로 조회한다.
        QueueEntryHistory queueEntryHistory
            = queueEntryHistoryRepository
                .findByQueueKeyWithPessimisticWriteLock(queueEntryKey)
                .orElseThrow(QueueEntryNotFoundException::new);

        // 토큰 소비와 취소가 동시에 상태를 변경하지 않도록 활성 입장 토큰을 비관적 락으로 조회한다.
        AdmissionToken activeToken
            = admissionTokenRepository
                .findByGame_IdAndUser_IdAndStatusWithPessimisticWriteLock(gameId, userId, AdmissionTokenStatus.ACTIVE)
                .orElse(null);

        // ADMITTED 이력은 함께 회수할 ACTIVE 토큰이 있을 때만 취소한다.
        if (queueEntryHistory.getStatus() == QueueEntryHistoryStatus.ADMITTED && Objects.isNull(activeToken)) {
            throw new QueueEntryCancellationTokenRequiredException();
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
                queueZSet.remove(waitingQueueRedisKey, redisMember);
            }
        });

        return QueueCancelResponse.builder().gameId(gameId).queueStatus(QueueEntryHistoryStatus.CANCELED).build();
    }

    /**
     * 경기 존재 여부와 예매 가능 상태, 사용자의 존재 여부를 확인하고
     * 이전 거절 결과를 삭제한 뒤 Kafka로 대기열 진입 이벤트를 발행한다.
     *
     * @param gameId 대기열에 진입할 경기 ID
     * @param userId 대기열 진입을 요청한 사용자 ID
     * @return Kafka 이벤트 발행 결과를 나타내는 비동기 작업
     */
    @Transactional(readOnly = true)
    public CompletableFuture<Void> requestQueueEntry(Long gameId, Long userId) {

        // 처리할 수 없는 이벤트가 Kafka에 발행되지 않도록 경기 존재 여부와 예매 가능 상태, 사용자의 존재 여부를 먼저 확인한다.
        Game game = gameRepository.findById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));

        if (game.getBookingStatus() != BookingStatus.OPEN) {
            throw new BookingNotOpenException();
        }

        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException();
        }

        // 이전 비동기 요청의 거절 결과가 새 SSE 연결에 전달되지 않도록 이벤트 발행 전에 삭제한다.
        queueEntryRejectionService.deleteRejection(gameId, userId);

        // eventId는 이벤트 로그 추적에 사용하고, requestedAt은 Redis ZSet의 대기 순서를 결정하는 기준으로 사용한다.
        QueueEntryRequestedEvent event = new QueueEntryRequestedEvent(UUID.randomUUID(), gameId, userId, Instant.now());

        // Kafka 발행 실패는 대기열 진입 요청 실패로 변환하여 비동기 작업을 예외 상태로 완료한다.
        return queueEntryEventPublisher.publish(event).thenAccept(result -> {}).exceptionally(exception -> {
            throw new QueueEventPublishFailedException(exception);
        });
    }

    /**
     * Kafka 대기열 진입 이벤트를 바탕으로 DB 대기 이력과 Redis 대기열을 등록한다.
     * <p>사용자 행에 비관적 락을 적용하여 동일 사용자의 여러 경기 대기열 등록을 순서대로 처리한다.</p>
     * <p>사용자가 정책상 등록할 수 없는 요청은 예외 대신 거절 사유로 반환하여
     * Consumer가 Redis 결과를 저장하고 이벤트 처리를 완료할 수 있게 한다.</p>
     *
     * @param event 대기열 진입 요청 이벤트
     * @return 사용자에게 전달할 거절 사유, 거절하지 않으면 빈 값
     */
    @Transactional
    public Optional<QueueEntryRejectionReason> registerQueueEntry(QueueEntryRequestedEvent event) {

        // 잘못된 이벤트는 재시도해도 처리할 수 없으므로 DB와 Redis에 접근하기 전에 검증한다.
        validateQueueEntryEvent(event);

        Game game
            = gameRepository.findById(event.gameId()).orElseThrow(() -> new GameNotFoundException(event.gameId()));

        // 이벤트 발행 후 경기 상태가 바뀔 수 있으므로 실제 등록 직전에 예매 가능 상태를 다시 확인한다.
        if (game.getBookingStatus() != BookingStatus.OPEN) {
            return Optional.of(QueueEntryRejectionReason.BOOKING_NOT_OPEN);
        }

        // 동일 사용자의 여러 경기 대기열 등록이 동시에 처리되지 않도록 사용자 행을 잠근다.
        User user
            = queueUserRepository
                .findByIdWithPessimisticWriteLock(event.userId())
                .orElseThrow(UserNotFoundException::new);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime requestedAt = event.requestedAt().atZone(ZoneId.systemDefault()).toLocalDateTime();

        String waitingQueueRedisKey = waitingQueueRedisKey(event.gameId());
        String redisMember = redisMember(event.userId());
        String queueEntryKey = queueEntryKey(event.gameId(), event.userId());
        long score = event.requestedAt().toEpochMilli();

        ActiveTokenCheckResult activeTokenCheckResult
            = checkActiveTokens(user.getId(), game.getId(), queueEntryKey, now);
        boolean hasWaitingEntryInAnotherGame
            = queueEntryHistoryRepository
                .existsByUser_IdAndGame_IdNotAndStatus(user.getId(), game.getId(), QueueEntryHistoryStatus.WAITING);

        // 현재 경기의 활성 Queue-Token은 기존 admit 흐름을 이어가고,
        // 다른 경기에서 대기하거나 토큰을 사용 중이면 새 대기열을 만들지 않는다.
        if (activeTokenCheckResult.hasUsableCurrentGameToken()) {
            return Optional.empty();
        } else if (activeTokenCheckResult.hasUsableOtherGameToken()) {
            return Optional.of(QueueEntryRejectionReason.ACTIVE_QUEUE_TOKEN_IN_ANOTHER_GAME);
        } else if (hasWaitingEntryInAnotherGame) {
            return Optional.of(QueueEntryRejectionReason.WAITING_IN_OTHER_GAME);
        }

        ZSetOperations<String, String> queueZSet = getZSetOperations();

        QueueEntryHistory existingHistory = queueEntryHistoryRepository.findByQueueKey(queueEntryKey).orElse(null);

        // 동일 경기와 사용자의 DB 이력이 있으면 새로운 이력을 중복 생성하지 않는다.
        // 기존 상태가 WAITING이면 Consumer 재처리 과정에서 누락됐을 수 있는 Redis 대기열 정보만 복구한다.
        if (Objects.nonNull(existingHistory)) {
            if (existingHistory.getStatus() == QueueEntryHistoryStatus.WAITING) {
                addRedisQueueEntryIfAbsent(queueZSet, waitingQueueRedisKey, redisMember, score);
            }

            // 취소된 이력은 새 요청 시간으로 갱신하고 DB 커밋 후 Redis 점수도 덮어써 대기열 맨 뒤에 등록한다.
            if (existingHistory.getStatus() == QueueEntryHistoryStatus.CANCELED) {
                // 이번 요청에서 토큰 만료로 취소됐거나 취소 시간보다 늦게 발행된 요청만 재진입으로 처리한다.
                if (existingHistory.getCanceledAt().isBefore(requestedAt)
                    || activeTokenCheckResult.currentQueueHistoryCanceled()) {
                    existingHistory.reenter(requestedAt);
                    // DB 커밋이 완료된 경우에만 Redis 대기 순서를 갱신하여 두 저장소의 상태 불일치를 방지한다.
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            addOrUpdateRedisQueueEntry(queueZSet, waitingQueueRedisKey, redisMember, score);
                        }
                    });
                }
            }

            return Optional.empty();
        }

        // Redis에 등록하기 전에 DB 이력을 즉시 반영하여 queueEntryKey 중복 여부를 먼저 확인한다.
        queueEntryHistoryRepository.saveAndFlush(QueueEntryHistory.of(game, user, queueEntryKey, now));

        addRedisQueueEntryIfAbsent(queueZSet, waitingQueueRedisKey, redisMember, score);
        return Optional.empty();
    }

    private ZSetOperations<String, String> getZSetOperations() {
        return redisTemplate.opsForZSet();
    }

    // 경기별 대기열 Redis ZSet key: queue:waiting:game:{gameId}
    private String waitingQueueRedisKey(Long gameId) {

        return "queue:waiting:game:%d".formatted(gameId);
    }

    // 대기열 사용자: user:{userId}
    private String redisMember(Long userId) {
        return "user:" + userId;
    }

    // DB 대기 이력 식별 key: queue:entry:game:{gameId}:user:{userId}
    private String queueEntryKey(Long gameId, Long userId) {

        return "queue:entry:game:%d:user:%d".formatted(gameId, userId);
    }

    /**
     * 대기열 진입 이벤트의 필수 값과 경기 · 사용자 ID를 검증한다.
     *
     * @param event 검증할 대기열 진입 요청 이벤트
     */
    private void validateQueueEntryEvent(QueueEntryRequestedEvent event) {

        if (Objects.isNull(event)) {
            throw new QueueEntryEventRequiredException();
        }

        if (Objects.isNull(event.eventId())) {
            throw new QueueEntryEventIdRequiredException();
        }

        if (Objects.isNull(event.gameId()) || event.gameId() <= 0) {
            throw new QueueEntryEventGameIdInvalidException();
        }

        if (Objects.isNull(event.userId()) || event.userId() <= 0) {
            throw new QueueEntryEventUserIdInvalidException();
        }

        if (Objects.isNull(event.requestedAt())) {
            throw new QueueEntryEventRequestedAtRequiredException();
        }
    }

    /**
     * Redis 대기열에 등록되지 않은 사용자만 요청 시간 점수로 추가한다.
     *
     * @param queueZSet Redis ZSet 연산 객체
     * @param waitingQueueRedisKey 경기별 Redis 대기열 Key
     * @param redisMember 대기열 사용자 member
     * @param score 대기 순서를 결정하는 요청 시간 점수
     */
    private void addRedisQueueEntryIfAbsent(
        ZSetOperations<String, String> queueZSet,
        String waitingQueueRedisKey,
        String redisMember,
        long score
    ) {

        // 이미 등록된 사용자는 기존 점수를 유지하고 등록되지 않은 사용자만 전달받은 요청 시간으로 추가한다.
        Boolean registered = queueZSet.addIfAbsent(waitingQueueRedisKey, redisMember, score);

        // 결과 자체가 반환되지 않은 경우에만 등록 실패로 처리한다.
        if (Objects.isNull(registered)) {
            throw new QueueRegistrationFailedException();
        }
    }

    /**
     * Redis 대기열에 사용자를 추가하거나 요청 시간 점수로 대기 순서를 갱신한다.
     *
     * @param queueZSet Redis ZSet 연산 객체
     * @param waitingQueueRedisKey 경기별 Redis 대기열 Key
     * @param redisMember 대기열 사용자 member
     * @param score 대기 순서를 결정하는 요청 시간 점수
     */
    private void addOrUpdateRedisQueueEntry(
        ZSetOperations<String, String> queueZSet,
        String waitingQueueRedisKey,
        String redisMember,
        long score
    ) {

        // 등록 여부와 관계없이 새 요청 시간으로 점수를 반영해 대기 순서를 갱신한다.
        Boolean registered = queueZSet.add(waitingQueueRedisKey, redisMember, score);

        // 결과 자체가 반환되지 않은 경우에만 등록 실패로 처리한다.
        if (Objects.isNull(registered)) {
            throw new QueueRegistrationFailedException();
        }
    }

    /**
     * 사용자의 활성 입장 토큰을 비관적 락으로 조회하고 만료 상태를 반영한다.
     * <p>만료된 토큰과 연결된 입장 허용 이력은 함께 취소하고,
     * 남은 활성 토큰을 현재 요청 경기와 다른 경기로 구분한다.</p>
     *
     * @param userId 조회할 사용자 ID
     * @param currentGameId 현재 요청한 경기 ID
     * @param currentQueueEntryKey 현재 요청한 경기와 사용자의 DB 대기 이력 식별키
     * @param currentTime 만료 여부를 판단할 시간
     * @return 현재 경기 · 다른 경기의 사용 가능한 활성 토큰과 현재 요청 경기의 이력 취소 여부
     */
    private ActiveTokenCheckResult checkActiveTokens(
        Long userId,
        Long currentGameId,
        String currentQueueEntryKey,
        LocalDateTime currentTime
    ) {
        List<AdmissionToken> activeTokens
            = admissionTokenRepository
                .findByUser_IdAndStatusWithPessimisticWriteLock(userId, AdmissionTokenStatus.ACTIVE);

        boolean currentQueueHistoryCanceled = false;

        for (AdmissionToken token : activeTokens) {
            // 전체 유효시간 만료를 최초 좌석 탐색 만료보다 먼저 반영한다.
            if (token.isExpiredAt(currentTime)) {
                token.expire(currentTime);
                if (cancelAdmittedHistoryForExpiredToken(token, currentTime, currentQueueEntryKey)) {
                    currentQueueHistoryCanceled = true;
                }
            } else if (token.isSeatBrowsingExpiredAt(currentTime)) {
                token.expireBrowsing(currentTime);
                if (cancelAdmittedHistoryForExpiredToken(token, currentTime, currentQueueEntryKey)) {
                    currentQueueHistoryCanceled = true;
                }
            }
        }

        boolean hasUsableCurrentGameToken
            = activeTokens
                .stream()
                .anyMatch(
                    token -> token.getStatus() == AdmissionTokenStatus.ACTIVE
                        && token.getGame().getId().equals(currentGameId)
                );

        boolean hasUsableOtherGameToken
            = activeTokens
                .stream()
                .anyMatch(
                    token -> token.getStatus() == AdmissionTokenStatus.ACTIVE
                        && !token.getGame().getId().equals(currentGameId)
                );

        return new ActiveTokenCheckResult(
            hasUsableCurrentGameToken,
            hasUsableOtherGameToken,
            currentQueueHistoryCanceled
        );
    }

    /**
     * 만료된 Queue-Token과 연결된 입장 허용 이력을 취소한다.
     *
     * @param admissionToken 만료된 Queue-Token
     * @param currentTime 대기 이력을 취소할 시간
     * @param currentQueueEntryKey 현재 요청한 경기와 사용자의 DB 대기 이력 식별키
     * @return 현재 요청 경기의 입장 허용 이력을 취소했다면 true
     */
    private boolean cancelAdmittedHistoryForExpiredToken(
        AdmissionToken admissionToken,
        LocalDateTime currentTime,
        String currentQueueEntryKey
    ) {

        String queueEntryKey = queueEntryKey(admissionToken.getGame().getId(), admissionToken.getUser().getId());

        // 만료된 토큰의 입장 허용 이력을 취소해 동일 경기 대기열 재진입을 허용한다.
        Optional<QueueEntryHistory> admittedHistory
            = queueEntryHistoryRepository
                .findByQueueKeyWithPessimisticWriteLock(queueEntryKey)
                .filter(history -> history.getStatus() == QueueEntryHistoryStatus.ADMITTED);

        if (admittedHistory.isEmpty()) {
            return false;
        }

        admittedHistory.get().cancel(currentTime);

        return queueEntryKey.equals(currentQueueEntryKey);
    }

    /**
     * 활성 Queue-Token 확인 결과
     *
     * @param hasUsableCurrentGameToken 현재 요청 경기에서 계속 사용할 수 있는 활성 토큰 존재 여부
     * @param hasUsableOtherGameToken 다른 경기에서 사용 중인 활성 토큰 존재 여부
     * @param currentQueueHistoryCanceled 현재 요청 경기의 입장 이력이 토큰 만료로 취소됐는지 여부
     */
    private record ActiveTokenCheckResult(
        boolean hasUsableCurrentGameToken,
        boolean hasUsableOtherGameToken,
        boolean currentQueueHistoryCanceled
    ) {
    }
}
