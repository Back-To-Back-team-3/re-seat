package com.backtoback.reseat.domain.queue.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.backtoback.reseat.domain.queue.entity.QueueEntryRejectionReason;

import lombok.RequiredArgsConstructor;

/**
 * 대기열 진입 요청의 최신 식별자와 거절 결과의 Redis 저장 및 수명주기를 관리한다.
 * <p>사용자와 경기별 최신 요청만 거절 결과를 저장할 수 있게 하고,
 * 전달되지 않은 결과와 최신 요청 식별자는 TTL이 지나면 자동으로 정리한다.</p>
 */
@Service
@RequiredArgsConstructor
public class QueueEntryRejectionService {

    private static final Duration REJECTION_TTL = Duration.ofMinutes(2);

    // Consumer 재시도와 처리 대기시간을 고려해 최신 요청 식별자를 거절 결과보다 길게 유지한다.
    private static final Duration LATEST_REQUEST_TTL = Duration.ofMinutes(5);

    // 최신 eventId와 일치할 때만 거절 결과를 저장하고 최신 요청 식별자를 제거한다.
    private static final DefaultRedisScript<Long> SAVE_REJECTION_IF_LATEST_SCRIPT = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) ~= ARGV[1] then
            return 0
        end
        redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
        redis.call('DEL', KEYS[1])
        return 1
        """, Long.class);

    // 최신 eventId와 일치할 때만 최신 요청 식별자를 제거한다.
    private static final DefaultRedisScript<Long> COMPLETE_REQUEST_IF_LATEST_SCRIPT = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) ~= ARGV[1] then
            return 0
        end
        redis.call('DEL', KEYS[1])
        return 1
        """, Long.class);

    // 최신 요청 식별자 저장과 이전 거절 결과 삭제를 하나의 Redis 연산으로 처리한다.
    private static final DefaultRedisScript<Long> PREPARE_REQUEST_SCRIPT = new DefaultRedisScript<>("""
        redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
        redis.call('DEL', KEYS[2])
        return 1
        """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    // Consumer 거절 결과 key: queue:entry:rejection:game:{gameId}:user:{userId}
    private String rejectionKey(Long gameId, Long userId) {

        return "queue:entry:rejection:game:%d:user:%d".formatted(gameId, userId);
    }

    // 최신 대기열 진입 요청 eventId key: queue:entry:request:latest:game:{gameId}:user:{userId}
    private String latestRequestKey(Long gameId, Long userId) {

        return "queue:entry:request:latest:game:%d:user:%d".formatted(gameId, userId);
    }

    /**
     * 사용자와 경기별 최신 대기열 진입 요청을 기록하고 이전 거절 결과를 삭제한다.
     * <p>최신 요청 식별자 저장과 이전 거절 결과 삭제를 Redis에서 원자적으로 처리하여
     * 서로 다른 요청의 갱신이 사이에 끼어들지 못하게 한다.</p>
     *
     * @param gameId 진입을 요청한 경기 ID
     * @param userId 대기열 진입을 요청한 사용자 ID
     * @param eventId 최신 대기열 진입 요청 이벤트 ID
     */
    public void prepareRequest(Long gameId, Long userId, UUID eventId) {

        String latestRequestKey = latestRequestKey(gameId, userId);
        String rejectionKey = rejectionKey(gameId, userId);

        // 저장과 삭제 사이에 다른 요청이 끼어들지 않도록 Redis Lua Script에서 원자적으로 처리한다.
        redisTemplate
            .execute(
                PREPARE_REQUEST_SCRIPT,
                List.of(latestRequestKey, rejectionKey),
                eventId.toString(),
                String.valueOf(LATEST_REQUEST_TTL.toMillis())
            );
    }

    /**
     * 현재 이벤트가 최신 요청이면 거절 사유를 저장하고 최신 요청 식별자를 삭제한다.
     * <p>최신 요청 비교와 거절 결과 저장을 Redis에서 원자적으로 처리하여
     * 지연된 이전 이벤트가 최신 요청 결과를 덮어쓰지 못하게 한다.</p>
     *
     * @param gameId 진입을 요청한 경기 ID
     * @param userId 대기열 진입을 요청한 사용자 ID
     * @param eventId 처리한 대기열 진입 요청 이벤트 ID
     * @param reason Consumer가 확인한 거절 사유
     * @return 최신 요청의 거절 결과를 저장했다면 true
     */
    public boolean saveRejectionIfLatest(Long gameId, Long userId, UUID eventId, QueueEntryRejectionReason reason) {

        String latestRequestKey = latestRequestKey(gameId, userId);
        String rejectionKey = rejectionKey(gameId, userId);

        // 비교와 저장 사이에 최신 요청이 바뀌지 않도록 Redis Lua Script에서 원자적으로 처리한다.
        Long scriptResult
            = redisTemplate
                .execute(
                    SAVE_REJECTION_IF_LATEST_SCRIPT,
                    List.of(latestRequestKey, rejectionKey),
                    eventId.toString(),
                    reason.name(),
                    String.valueOf(REJECTION_TTL.toMillis())
                );

        return Long.valueOf(1L).equals(scriptResult);
    }

    /**
     * 현재 이벤트가 최신 요청이면 최신 요청 식별자를 삭제한다.
     * <p>거절 사유 없이 정상 처리된 최신 요청을 완료 상태로 정리하며,
     * 지연된 이전 이벤트는 최신 요청 식별자를 삭제하지 못하게 한다.</p>
     *
     * @param gameId 진입을 요청한 경기 ID
     * @param userId 대기열 진입을 요청한 사용자 ID
     * @param eventId 처리한 대기열 진입 요청 이벤트 ID
     * @return 최신 요청 식별자를 삭제했다면 true
     */
    public boolean completeRequestIfLatest(Long gameId, Long userId, UUID eventId) {

        String latestRequestKey = latestRequestKey(gameId, userId);

        // 비교와 삭제 사이에 최신 요청이 바뀌지 않도록 Redis Lua Script에서 원자적으로 처리한다.
        Long scriptResult
            = redisTemplate.execute(COMPLETE_REQUEST_IF_LATEST_SCRIPT, List.of(latestRequestKey), eventId.toString());

        return Long.valueOf(1L).equals(scriptResult);
    }
}
