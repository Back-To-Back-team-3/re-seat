package com.backtoback.reseat.domain.queue.service;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.dto.event.QueueEntryRequestedEvent;
import com.backtoback.reseat.domain.queue.dto.response.QueueStatusResponse;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistory;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.queue.repository.QueueEntryHistoryRepository;
import com.backtoback.reseat.domain.queue.repository.QueueUserRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/**
 * QueueService의 대기 상태 조회와 중복 참여 방지를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QueueService")
public class QueueServiceTest {

    private static final Long GAME_ID = 1L;
    private static final Long USER_ID = 1L;

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private QueueEntryHistoryRepository queueEntryHistoryRepository;
    @Mock private AdmissionTokenRepository admissionTokenRepository;
    @Mock private GameRepository gameRepository;
    @Mock private UserRepository userRepository;
    @Mock private QueueUserRepository queueUserRepository;
    @Mock private QueueEntryEventPublisher queueEntryEventPublisher;
    @InjectMocks private QueueService queueService;

    private QueueEntryRequestedEvent givenQueueEntryRequest() {

        Game game = mock(Game.class);
        User user = mock(User.class);
        given(game.getId()).willReturn(GAME_ID);
        given(user.getId()).willReturn(USER_ID);
        given(gameRepository.findById(GAME_ID))
                .willReturn(Optional.of(game));
        given(queueUserRepository
                .findByIdWithPessimisticWriteLock(USER_ID))
                .willReturn(Optional.of(user));

        return new QueueEntryRequestedEvent(
                UUID.randomUUID(),
                GAME_ID,
                USER_ID,
                Instant.now()
        );
    }

    @Test
    @DisplayName("대기 중인 사용자는 현재 순번과 예상 대기시간을 조회한다.")
    void getMyQueueStatus_whenWaiting_returnsStatus() {

        // given
        // Redis 순번 20은 사용자 순번 21이며, 두 번째 자동 입장 주기인 6초로 계산된다.
        given(redisTemplate.opsForZSet())
                .willReturn(zSetOperations);
        given(admissionTokenRepository
                .findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(
                        eq(GAME_ID),
                        eq(USER_ID),
                        eq(AdmissionTokenStatus.ACTIVE),
                        any(LocalDateTime.class)
                )
        ).willReturn(Optional.empty());
        given(zSetOperations
                .rank("queue:game:1", "user:1"))
                .willReturn(20L);

        // when
        QueueStatusResponse myQueueStatus = queueService.getMyQueueStatus(GAME_ID, USER_ID);

        // then
        assertThat(myQueueStatus.getRank())
                .isEqualTo(21L);
        assertThat(myQueueStatus.getEstimatedWaitSeconds())
                .isEqualTo(6L);
        assertThat(myQueueStatus.getQueueStatus())
                .isEqualTo(QueueEntryHistoryStatus.WAITING);
        assertThat(myQueueStatus.isAdmitted())
                .isFalse();

        then(zSetOperations)
                .should()
                .rank("queue:game:1", "user:1");
    }

    @Test
    @DisplayName("다른 경기에서 대기 중인 사용자는 새 대기열에 등록하지 않는다.")
    void registerQueueEntry_whenWaitingInAnotherGame_doesNotRegister() {

        // given
        // 사용자 행을 잠근 뒤 다른 경기의 WAITING 이력을 확인해 동일 사용자의 중복참여를 막는다.
        QueueEntryRequestedEvent event = givenQueueEntryRequest();
        given(admissionTokenRepository
                .existsByUser_IdAndStatusAndExpiresAtAfter(
                        eq(USER_ID),
                        eq(AdmissionTokenStatus.ACTIVE),
                        any(LocalDateTime.class)
                )
        ).willReturn(false);
        given(queueEntryHistoryRepository
                .existsByUser_IdAndGame_IdNotAndStatus(
                        eq(USER_ID),
                        eq(GAME_ID),
                        eq(QueueEntryHistoryStatus.WAITING)
                )
        ).willReturn(true);

        // when
        queueService.registerQueueEntry(event);

        // then
        // 중복 참여 대상이면 DB 이력을 생성하거나 Redis 대기열에 등록하지 않는다.
        then(redisTemplate)
                .shouldHaveNoInteractions();
        then(queueEntryHistoryRepository)
                .should(never())
                .saveAndFlush(any(QueueEntryHistory.class));
    }

    @Test
    @DisplayName("유효한 ACTIVE 토큰이 있는 사용자는 새 대기열에 등록하지 않는다.")
    void registerQueueEntry_whenActiveTokenExists_doesNotRegister() {

        // given
        // 유효한 ACTIVE 토큰이 있으면 Queue-Token을 보유한 사용자의 대기열 재진입을 막는다.
        QueueEntryRequestedEvent event = givenQueueEntryRequest();
        given(admissionTokenRepository
                .existsByUser_IdAndStatusAndExpiresAtAfter(
                        eq(USER_ID),
                        eq(AdmissionTokenStatus.ACTIVE),
                        any(LocalDateTime.class)
                )
        ).willReturn(true);
        given(queueEntryHistoryRepository
                .existsByUser_IdAndGame_IdNotAndStatus(
                        eq(USER_ID),
                        eq(GAME_ID),
                        eq(QueueEntryHistoryStatus.WAITING)
                )
        ).willReturn(false);

        // when
        queueService.registerQueueEntry(event);

        // then
        // 유효한 ACTIVE 토큰 보유자는 DB 이력을 생성하거나 Redis 대기열에 등록하지 않는다.
        then(redisTemplate)
                .shouldHaveNoInteractions();
        then(queueEntryHistoryRepository)
                .should(never())
                .saveAndFlush(any(QueueEntryHistory.class));
    }
}
