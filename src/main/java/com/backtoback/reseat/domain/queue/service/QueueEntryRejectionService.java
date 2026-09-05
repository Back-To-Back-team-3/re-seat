package com.backtoback.reseat.domain.queue.service;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.backtoback.reseat.domain.queue.entity.QueueEntryRejectionReason;

import lombok.RequiredArgsConstructor;

/**
 * 대기열 진입 거절 결과의 Redis 저장과 수명주기를 관리한다.
 * <p>사용자와 경기별로 최신 거절 사유 하나만 유지하고
 * 전달되지 않은 결과는 TTL이 지나면 자동으로 정리한다.</p>
 */
@Service
@RequiredArgsConstructor
public class QueueEntryRejectionService {

    private static final Duration REJECTION_TTL = Duration.ofMinutes(2);
    private final RedisTemplate<String, String> redisTemplate;

    // Consumer 거절 결과 key: queue:entry:rejection:game:{gameId}:user:{userId}
    private String rejectionKey(Long gameId, Long userId) {
        return "queue:entry:rejection:game:%d:user:%d".formatted(gameId, userId);
    }

    /**
     * 사용자와 경기별 최신 대기열 진입 거절 사유를 저장한다.
     *
     * @param gameId 진입을 요청한 경기 ID
     * @param userId 진입을 요청한 사용자 ID
     * @param reason Consumer가 확인한 거절 사유
     */
    public void saveRejection(Long gameId, Long userId, QueueEntryRejectionReason reason) {

        String rejectionKey = rejectionKey(gameId, userId);

        // 같은 사용자와 경기의 최신 거절 사유만 유지하고 저장할 때마다 전달 가능 시간을 갱신한다.
        redisTemplate.opsForValue().set(rejectionKey, reason.name(), REJECTION_TTL);
    }

    /**
     * 사용자와 경기의 이전 대기열 진입 거절 결과를 삭제한다.
     *
     * @param gameId 진입을 요청한 경기 ID
     * @param userId 진입을 요청한 사용자 ID
     */
    public void deleteRejection(Long gameId, Long userId) {

        String rejectionKey = rejectionKey(gameId, userId);
        redisTemplate.delete(rejectionKey);
    }
}
