package com.backtoback.reseat.domain.queue.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Redis 경기별 대기열의 사용자를 주기적으로 입장 허용 처리하는 스케줄러
 *
 * <p>Redis SCAN으로 경기별 대기열 키를 조회하고,
 * 기존 입장 허용 서비스를 호출해 대기열 앞 사용자의 입장 토큰을 발급한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

    // 입장 허용할 최대 사용자 수
    private static final int ADMIT_LIMIT = 20;

    // 자동 입장 처리 최초 실행 지연 및 반복 간격
    private static final long ADMISSION_INTERVAL_MILLIS = 3000L;

    private final RedisTemplate<String, String> redisTemplate;
    private final AdmissionTokenService admissionTokenService;

    /**
     * Redis에 존재하는 경기별 대기열을 조회하고 앞 순서 사용자를 입장 허용한다.
     */
    @Scheduled(fixedDelay = ADMISSION_INTERVAL_MILLIS, initialDelay = ADMISSION_INTERVAL_MILLIS)
    public void admitWaitingUsers() {

        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(redisKeyPattern())
                .count(100)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                admitQueue(cursor.next());
            }
        }
    }

    // Redis ZSet key 검색 패턴
    private String redisKeyPattern() {
        return "queue:game:*";
    }

    // Redis ZSet key에서 gameId 추출
    private Long parseGameId(String redisKey) {
        String prefix = "queue:game:";
        return Long.parseLong(redisKey.substring(prefix.length()));
    }

    // 경기별 대기열 사용자를 입장 허용 처리한다.
    private void admitQueue(String redisKey) {
        try {
            Long gameId = parseGameId(redisKey);
            admissionTokenService.admit(gameId, ADMIT_LIMIT);
        } catch (RuntimeException e) {
            log.error(
                    "대기열 자동 입장 처리 실패. redisKey={}",
                    redisKey, e
            );
        }
    }
}
