package com.backtoback.reseat.domain.queue.service;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistory;
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

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdmissionTokenService {

    private static final long TOKEN_TTL_MINUTES = 5L;

    private final RedisTemplate<String, String> redisTemplate;
    private final AdmissionTokenRepository admissionTokenRepository;
    private final QueueEntryHistoryRepository queueEntryHistoryRepository;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;

    @Transactional
    public int admit(Long gameId, int limit) {
        ZSetOperations<String, String> queueZSet = getZSetOperations();

        String redisKey = redisKey(gameId);

        // Redis ZSet 앞쪽 사용자부터 limit명 까지 입장 허용 대상
        Set<String> members = queueZSet.range(redisKey, 0, limit - 1);

        if (members == null || members.isEmpty()) {
            return 0;
        }

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("경기를 찾을 수 없습니다."));

        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = issuedAt.plusMinutes(TOKEN_TTL_MINUTES);

        int admittedCount = 0;

        for (String member: members) {
            Long userId = parseUserId(member);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

            String token = createQueueToken();

            admissionTokenRepository.save(AdmissionToken.of(game, user, token, issuedAt, expiresAt));

            String queueKey = queueKey(gameId, userId);

            QueueEntryHistory queueEntryHistory =
                    queueEntryHistoryRepository.findByQueueKey(queueKey)
                            .orElseThrow(() -> new IllegalArgumentException("대기열 진입 이력이 없습니다."));

            queueEntryHistory.admit(issuedAt);

            // 입장 허용된 사용자는 대기열 순번에서 제거
            queueZSet.remove(redisKey, member);
            admittedCount++;
        }

        return admittedCount;
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
        return Long.parseLong(member.replace("user:", ""));
    }

    private String createQueueToken() {
        return "qt_" + UUID.randomUUID();
    }
}
