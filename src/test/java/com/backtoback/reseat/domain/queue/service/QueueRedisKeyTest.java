package com.backtoback.reseat.domain.queue.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backtoback.reseat.domain.queue.exception.QueueRedisKeyInvalidException;
import com.backtoback.reseat.domain.queue.exception.QueueRedisMemberInvalidException;

/**
 * Queue 도메인의 Redis Key · member와 대기 이력 식별키 규칙을 검증한다.
 */
@DisplayName("QueueRedisKey")
public class QueueRedisKeyTest {

    private static final Long GAME_ID = 1L;
    private static final Long USER_ID = 1L;

    // ---------- Queue Redis 저장 형식 생성 ----------

    @Test
    @DisplayName("Queue Redis Key와 member를 기존 저장 형식으로 생성한다.")
    void createsQueueRedisValues_withCurrentFormats() {

        // given
        // 클래스 상수 GAME_ID와 USER_ID를 사용한다.

        // when
        String waitingKey = QueueRedisKey.waiting(GAME_ID);
        String waitingPattern = QueueRedisKey.waitingPattern();
        String member = QueueRedisKey.member(USER_ID);
        String entryKey = QueueRedisKey.entry(GAME_ID, USER_ID);
        String rejectionKey = QueueRedisKey.rejection(GAME_ID, USER_ID);
        String latestRequestKey = QueueRedisKey.latestRequest(GAME_ID, USER_ID);
        String admissionLockKey = QueueRedisKey.admissionLock(GAME_ID);

        // then
        // 기존 Redis 데이터와 DB 대기 이력을 계속 조회할 수 있도록 문자열 형식 자체를 검증한다.
        assertThat(waitingKey).isEqualTo("queue:waiting:game:1");
        assertThat(waitingPattern).isEqualTo("queue:waiting:game:*");
        assertThat(member).isEqualTo("user:1");
        assertThat(entryKey).isEqualTo("queue:entry:game:1:user:1");
        assertThat(rejectionKey).isEqualTo("queue:entry:rejection:game:1:user:1");
        assertThat(latestRequestKey).isEqualTo("queue:entry:request:latest:game:1:user:1");
        assertThat(admissionLockKey).isEqualTo("lock:queue:admit:1");
    }

    // ---------- Queue Redis Key 파싱 ----------

    @Test
    @DisplayName("경기별 Redis 대기열 Key에서 경기 ID를 추출한다.")
    void parseGameId_withWaitingKey_returnsGameId() {

        // given
        String waitingKey = QueueRedisKey.waiting(GAME_ID);

        // when
        long parsedGameId = QueueRedisKey.parseGameId(waitingKey);

        // then
        assertThat(parsedGameId).isEqualTo(GAME_ID);
    }

    @Test
    @DisplayName("경기별 대기열 형식이 아닌 Redis Key에서는 전용 예외가 발생한다.")
    void parseGameId_withInvalidKey_throwsQueueRedisKeyInvalidException() {

        // given
        String invalidKey = "queue:invalid:game:1";

        // when & then
        assertThatThrownBy(() -> QueueRedisKey.parseGameId(invalidKey))
            .isInstanceOf(QueueRedisKeyInvalidException.class);
    }

    @Test
    @DisplayName("Redis 대기열 Key의 경기 ID가 숫자가 아니면 원인을 포함한 전용 예외가 발생한다.")
    void parseGameId_withNonNumericGameId_throwsExceptionWithCause() {

        // given
        String nonNumericGameIdKey = "queue:waiting:game:not-a-number";

        // when & then
        // 숫자 변환 실패 원인이 사라지지 않도록 cause가 NumberFormatException인지 함께 확인한다.
        assertThatThrownBy(() -> QueueRedisKey.parseGameId(nonNumericGameIdKey))
            .isInstanceOf(QueueRedisKeyInvalidException.class)
            .hasCauseInstanceOf(NumberFormatException.class);
    }

    // ---------- Queue Redis member 파싱 ----------

    @Test
    @DisplayName("Redis 대기열 사용자 member에서 사용자 ID를 추출한다.")
    void parseUserId_withValidMember_returnsUserId() {

        // given
        String validMember = QueueRedisKey.member(USER_ID);

        // when
        long parsedUserId = QueueRedisKey.parseUserId(validMember);

        // then
        assertThat(parsedUserId).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("Redis 대기열 사용자 형식이 아닌 member에서는 전용 예외가 발생한다.")
    void parseUserId_withInvalidMember_throwsQueueRedisMemberInvalidException() {

        // given
        String invalidMember = "member:1";

        // when & then
        assertThatThrownBy(() -> QueueRedisKey.parseUserId(invalidMember))
            .isInstanceOf(QueueRedisMemberInvalidException.class);
    }
}
