package com.backtoback.reseat.domain.queue.service;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.dto.response.AdmitEventResponse;
import com.backtoback.reseat.domain.queue.dto.response.QueueCancelResponse;
import com.backtoback.reseat.domain.queue.dto.response.QueueEnterResponse;
import com.backtoback.reseat.domain.queue.dto.response.QueueStatusResponse;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistory;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.queue.repository.QueueEntryHistoryRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.exception.UserNotFoundException;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

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

    /**
     * 사용자를 경기별 대기열에 진입시킨다.
     *
     * <p>이미 활성 입장 토큰이 있으면 대기열에 다시 넣지 않고 입장 허용 상태를 반환한다.
     * 최초 진입자는 Redis ZSet에 등록하고 DB 진입 이력을 남긴다.</p>
     *
     * @param gameId 경기 ID
     * @param userId 사용자 ID
     * @return 대기열 진입 또는 입장 허용 응답
     */
    @Transactional
    public QueueEnterResponse myQueueEnter(Long gameId, Long userId) {

        ZSetOperations<String, String> queueZSet = getZSetOperations();

        // Game - Exception 생기면 변경 (현재 임시)
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("경기를 찾을 수 없습니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        // 활성 입장 코튼이 있으면 대기열에 넣지 않고 기존 토큰 정보를 반환한다.
        AdmissionToken activeToken = admissionTokenRepository
                .findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(
                        gameId,
                        userId,
                        AdmissionTokenStatus.ACTIVE,
                        LocalDateTime.now())
                .orElse(null);

        if (Objects.nonNull(activeToken)) {
            return QueueEnterResponse.builder()
                    .gameId(gameId)
                    .rank(0L)
                    .estimatedWaitSeconds(0L)
                    .queueStatus(QueueEntryHistoryStatus.ADMITTED)
                    .admitted(true)
                    .queueToken(activeToken.getToken())
                    .tokenExpiresAt(activeToken.getExpiresAt())
                    .build();
        }

        String redisKey = redisKey(gameId);
        String redisMember = redisMember(userId);
        String queueKey = queueKey(gameId, userId);
        long score = System.currentTimeMillis();

        // 최초 대기열 진입일 경우에만 Redis ZSet에 등록
        Long redisRank = queueZSet.rank(redisKey, redisMember);

        if (Objects.isNull(redisRank)) {
            queueZSet.add(redisKey, redisMember, score);
            redisRank = queueZSet.rank(redisKey, redisMember);
        }

        if (Objects.isNull(redisRank)) {
            throw new IllegalStateException("대기열 등록에 실패했습니다.");
        }

        // 최초 대기열 진입 이력을 저장하되, 동시 진입으로 queue_key 유니크 제약 충돌이 발생하면 기존 이력을 다시 조회한다.
        queueEntryHistoryRepository.findByQueueKey(queueKey)
                .orElseGet(() -> {
                    try {
                        return queueEntryHistoryRepository.saveAndFlush(
                                QueueEntryHistory.of(game, user, queueKey, LocalDateTime.now()));
                    } catch (DataIntegrityViolationException e) {
                        return queueEntryHistoryRepository.findByQueueKey(queueKey)
                                .orElseThrow(() -> new IllegalStateException("대기열 진입 이력 저장에 실패했습니다.", e));
                    }
                });

        long rank = redisRank + 1;

        return QueueEnterResponse.builder()
                .gameId(gameId)
                .rank(rank)
                .estimatedWaitSeconds(null)
                .queueStatus(QueueEntryHistoryStatus.WAITING)
                .admitted(false)
                .queueToken(null)
                .tokenExpiresAt(null)
                .build();
    }

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
            throw new IllegalArgumentException("대기열 진입 이력이 없습니다.");
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
                .orElseThrow(() -> new IllegalArgumentException("활성화된 입장 토큰이 없습니다."));

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
                .orElseThrow(() -> new IllegalArgumentException("대기열 진입 이력이 없습니다."));

        queueEntryHistory.cancel(LocalDateTime.now());

        queueZSet.remove(redisKey, redisMember);

        return QueueCancelResponse.builder()
                .gameId(gameId)
                .queueStatus(QueueEntryHistoryStatus.CANCELED)
                .build();
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
}
