package com.backtoback.reseat.domain.queue.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import com.backtoback.reseat.domain.game.entity.BookingStatus;
import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.dto.event.QueueEntryRequestedEvent;
import com.backtoback.reseat.domain.queue.dto.response.QueueStatusResponse;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistory;
import com.backtoback.reseat.domain.queue.entity.QueueEntryHistoryStatus;
import com.backtoback.reseat.domain.queue.entity.QueueEntryRejectionReason;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.queue.repository.QueueEntryHistoryRepository;
import com.backtoback.reseat.domain.queue.repository.QueueUserRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;

/**
 * QueueService의 대기 상태 조회와 대기열 진입 · 거절 정책을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QueueService")
public class QueueServiceTest {

    private static final Long GAME_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final String WAITING_QUEUE_REDIS_KEY = "queue:waiting:game:" + GAME_ID;
    private static final String WAITING_QUEUE_REDIS_MEMBER = "user:" + USER_ID;

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private QueueEntryHistoryRepository queueEntryHistoryRepository;
    @Mock
    private AdmissionTokenRepository admissionTokenRepository;
    @Mock
    private GameRepository gameRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private QueueUserRepository queueUserRepository;
    @Mock
    private QueueEntryEventPublisher queueEntryEventPublisher;
    @Mock
    private QueueEntryRejectionService queueEntryRejectionService;
    @InjectMocks
    private QueueService queueService;

    private QueueEntryRequestedEvent givenQueueEntryRequest() {

        Game game = mock(Game.class);
        User user = mock(User.class);
        given(game.getId()).willReturn(GAME_ID);
        given(user.getId()).willReturn(USER_ID);
        given(gameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(game.getBookingStatus()).willReturn(BookingStatus.OPEN);
        given(queueUserRepository.findByIdWithPessimisticWriteLock(USER_ID)).willReturn(Optional.of(user));

        return new QueueEntryRequestedEvent(UUID.randomUUID(), GAME_ID, USER_ID, Instant.now());
    }

    @Test
    @DisplayName("대기 중인 사용자는 현재 순번과 예상 대기시간을 조회한다.")
    void getMyQueueStatus_whenWaiting_returnsStatus() {

        // given
        // Redis 순번 20은 사용자 순번 21이며, 두 번째 자동 입장 주기인 6초로 계산된다.
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(
            admissionTokenRepository
                .findByGame_IdAndUser_IdAndStatusAndExpiresAtAfter(
                    eq(GAME_ID),
                    eq(USER_ID),
                    eq(AdmissionTokenStatus.ACTIVE),
                    any(LocalDateTime.class)
                )
        ).willReturn(Optional.empty());
        given(zSetOperations.rank(WAITING_QUEUE_REDIS_KEY, WAITING_QUEUE_REDIS_MEMBER)).willReturn(20L);

        // when
        QueueStatusResponse myQueueStatus = queueService.getMyQueueStatus(GAME_ID, USER_ID);

        // then
        assertThat(myQueueStatus.getRank()).isEqualTo(21L);
        assertThat(myQueueStatus.getEstimatedWaitSeconds()).isEqualTo(6L);
        assertThat(myQueueStatus.getQueueStatus()).isEqualTo(QueueEntryHistoryStatus.WAITING);
        assertThat(myQueueStatus.isAdmitted()).isFalse();

        then(zSetOperations).should().rank(WAITING_QUEUE_REDIS_KEY, WAITING_QUEUE_REDIS_MEMBER);
    }

    @Test
    @DisplayName("다른 경기에서 대기 중인 사용자는 새 대기열에 등록하지 않고 거절 사유를 반환한다.")
    void registerQueueEntry_whenWaitingInAnotherGame_returnsRejectionReason() {

        // given
        // 사용자 행을 잠근 뒤 다른 경기의 WAITING 이력을 확인해 새 대기열 등록을 거절한다.
        QueueEntryRequestedEvent event = givenQueueEntryRequest();
        given(
            admissionTokenRepository
                .findByUser_IdAndStatusWithPessimisticWriteLock(eq(USER_ID), eq(AdmissionTokenStatus.ACTIVE))
        ).willReturn(List.of());
        given(
            queueEntryHistoryRepository
                .existsByUser_IdAndGame_IdNotAndStatus(eq(USER_ID), eq(GAME_ID), eq(QueueEntryHistoryStatus.WAITING))
        ).willReturn(true);

        // when
        Optional<QueueEntryRejectionReason> rejectionReason = queueService.registerQueueEntry(event);

        // then
        // 다른 경기 대기 거절 사유를 반환하고 DB 이력과 Redis 대기열을 생성하지 않는다.
        assertThat(rejectionReason).isEqualTo(Optional.of(QueueEntryRejectionReason.WAITING_IN_OTHER_GAME));
        then(redisTemplate).shouldHaveNoInteractions();
        then(queueEntryHistoryRepository).should(never()).saveAndFlush(any(QueueEntryHistory.class));
    }

    @Test
    @DisplayName("현재 경기의 유효한 ACTIVE 토큰이 있는 사용자는 새 대기열에 등록하지 않고 기존 입장 흐름을 유지한다.")
    void registerQueueEntry_whenCurrentGameActiveTokenExists_returnsEmpty() {

        // given
        // 현재 경기의 활성 Queue-Token이 있으면 새 대기열을 만들지 않고 기존 admit 흐름을 이어간다.
        QueueEntryRequestedEvent event = givenQueueEntryRequest();
        AdmissionToken admissionToken = mock(AdmissionToken.class);
        Game tokenGame = mock(Game.class);
        given(tokenGame.getId()).willReturn(GAME_ID);
        given(admissionToken.isExpiredAt(any(LocalDateTime.class))).willReturn(false);
        given(admissionToken.isSeatBrowsingExpiredAt(any(LocalDateTime.class))).willReturn(false);
        given(admissionToken.getStatus()).willReturn(AdmissionTokenStatus.ACTIVE);
        given(admissionToken.getGame()).willReturn(tokenGame);
        given(
            admissionTokenRepository
                .findByUser_IdAndStatusWithPessimisticWriteLock(eq(USER_ID), eq(AdmissionTokenStatus.ACTIVE))
        ).willReturn(List.of(admissionToken));
        given(
            queueEntryHistoryRepository
                .existsByUser_IdAndGame_IdNotAndStatus(eq(USER_ID), eq(GAME_ID), eq(QueueEntryHistoryStatus.WAITING))
        ).willReturn(false);

        // when
        Optional<QueueEntryRejectionReason> rejectionReason = queueService.registerQueueEntry(event);

        // then
        // 빈 거절 결과를 반환하고 DB 이력과 Redis 대기열을 생성하지 않는다.
        assertThat(rejectionReason).isEqualTo(Optional.empty());
        then(redisTemplate).shouldHaveNoInteractions();
        then(queueEntryHistoryRepository).should(never()).saveAndFlush(any(QueueEntryHistory.class));
    }
}
