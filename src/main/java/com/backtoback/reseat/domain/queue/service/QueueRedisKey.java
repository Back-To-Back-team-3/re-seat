package com.backtoback.reseat.domain.queue.service;

import com.backtoback.reseat.domain.queue.exception.QueueRedisKeyInvalidException;
import com.backtoback.reseat.domain.queue.exception.QueueRedisMemberInvalidException;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Queue 도메인에서 사용하는 Redis Key · member와 대기 이력 식별키 규칙.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QueueRedisKey {

    private static final String WAITING_PREFIX = "queue:waiting:game:";
    private static final String MEMBER_PREFIX = "user:";
    private static final String ENTRY_FORMAT = "queue:entry:game:%d:user:%d";
    private static final String REJECTION_FORMAT = "queue:entry:rejection:game:%d:user:%d";
    private static final String LATEST_REQUEST_FORMAT = "queue:entry:request:latest:game:%d:user:%d";
    private static final String ADMISSION_LOCK_PREFIX = "lock:queue:admit:";

    /**
     * 경기별 Redis 대기열 Key를 생성한다.
     *
     * @param gameId 경기 ID
     * @return 경기별 대기열 Key
     */
    public static String waiting(Long gameId) {

        return WAITING_PREFIX + gameId;
    }

    /**
     * 경기별 Redis 대기열을 SCAN하기 위한 검색 패턴을 반환한다.
     *
     * @return 경기별 대기열 검색 패턴
     */
    public static String waitingPattern() {

        return WAITING_PREFIX + "*";
    }

    /**
     * 경기별 Redis 대기열 Key에서 경기 ID를 추출한다.
     *
     * @param key 경기별 대기열 Key
     * @return 경기 ID
     */
    public static Long parseGameId(String key) {

        if (key == null || !key.startsWith(WAITING_PREFIX)) {
            throw new QueueRedisKeyInvalidException();
        }

        try {
            return Long.valueOf(key.substring(WAITING_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new QueueRedisKeyInvalidException(e);
        }
    }

    /**
     * Redis 대기열에 저장할 사용자 member를 생성한다.
     *
     * @param userId 사용자 ID
     * @return Redis 대기열 사용자 member
     */
    public static String member(Long userId) {

        return MEMBER_PREFIX + userId;
    }

    /**
     * Redis 대기열 사용자 member에서 사용자 ID를 추출한다.
     *
     * @param member Redis 대기열 사용자 member
     * @return 사용자 ID
     */
    public static Long parseUserId(String member) {

        if (member == null || !member.startsWith(MEMBER_PREFIX)) {
            throw new QueueRedisMemberInvalidException();
        }

        try {
            return Long.valueOf(member.substring(MEMBER_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new QueueRedisMemberInvalidException(e);
        }
    }

    /**
     * 경기와 사용자의 DB 대기 이력 식별키를 생성한다.
     *
     * @param gameId 경기 ID
     * @param userId 사용자 ID
     * @return DB 대기 이력 식별키
     */
    public static String entry(Long gameId, Long userId) {

        return ENTRY_FORMAT.formatted(gameId, userId);
    }

    /**
     * 대기열 진입 거절 결과를 저장할 Redis Key를 생성한다.
     *
     * @param gameId 경기 ID
     * @param userId 사용자 ID
     * @return 대기열 진입 거절 결과 Key
     */
    public static String rejection(Long gameId, Long userId) {

        return REJECTION_FORMAT.formatted(gameId, userId);
    }

    /**
     * 최신 대기열 진입 요청 eventId를 저장할 Redis Key를 생성한다.
     *
     * @param gameId 경기 ID
     * @param userId 사용자 ID
     * @return 최신 대기열 진입 요청 Key
     */
    public static String latestRequest(Long gameId, Long userId) {

        return LATEST_REQUEST_FORMAT.formatted(gameId, userId);
    }

    /**
     * 경기별 입장 처리를 위한 분산 락 Key를 생성한다.
     *
     * @param gameId 경기 ID
     * @return 경기별 입장 처리 분산 락 Key
     */
    public static String admissionLock(Long gameId) {

        return ADMISSION_LOCK_PREFIX + gameId;
    }
}
