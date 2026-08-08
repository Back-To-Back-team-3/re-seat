package com.backtoback.reseat.domain.queue.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.game.repository.GameRepository;
import com.backtoback.reseat.domain.queue.entity.AdmissionToken;
import com.backtoback.reseat.domain.queue.entity.AdmissionTokenStatus;
import com.backtoback.reseat.domain.queue.exception.QueueTokenExpiredException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenInvalidException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenRequiredException;
import com.backtoback.reseat.domain.queue.repository.AdmissionTokenRepository;
import com.backtoback.reseat.domain.queue.repository.QueueEntryHistoryRepository;
import com.backtoback.reseat.domain.user.entity.User;
import com.backtoback.reseat.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdmissionTokenService 토큰 검증 및 소비")
public class AdmissionTokenServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long GAME_ID = 10L;
    private static final String TOKEN = "qt_test";

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private AdmissionTokenRepository admissionTokenRepository;

    @Mock
    private QueueEntryHistoryRepository queueEntryHistoryRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdmissionTokenService admissionTokenService;

    private AdmissionToken activeToken() {

        LocalDateTime issuedAt = LocalDateTime.now().minusMinutes(1);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        return activeToken(issuedAt, expiresAt);
    }

    private AdmissionToken activeToken(LocalDateTime issuedAt, LocalDateTime expiresAt) {

        Game game = mock(Game.class);
        User user = mock(User.class);

        when(game.getId()).thenReturn(GAME_ID);
        when(user.getId()).thenReturn(USER_ID);

        return AdmissionToken.of(
            game,
            user,
            TOKEN,
            issuedAt,
            expiresAt
        );
    }

    @Test
    @DisplayName("사용자와 경기가 일치하는 ACTIVE 토큰은 검증에 성공한다.")
    void validateToken_withValidToken_succeeds() {

        AdmissionToken admissionToken = activeToken();

        when(admissionTokenRepository.findByToken(TOKEN))
            .thenReturn(Optional.of(admissionToken));

        assertThatCode(() -> admissionTokenService.validateToken(USER_ID, GAME_ID, TOKEN))
            .doesNotThrowAnyException();
        assertThat(admissionToken.getStatus())
            .isEqualTo(AdmissionTokenStatus.ACTIVE);

        verify(admissionTokenRepository).findByToken(TOKEN);
    }

    @Test
    @DisplayName("Queue-Token이 null이면 필수 토큰 예외가 발생한다.")
    void validateToken_withNullToken_throwsRequired() {

        assertThatThrownBy(() -> admissionTokenService.validateToken(USER_ID, GAME_ID, null))
            .isInstanceOf(QueueTokenRequiredException.class);

        verifyNoInteractions(admissionTokenRepository);
    }

    @Test
    @DisplayName("존재하지 않는 Queue-Token이면 유효하지 않은 토큰 예외가 발생한다.")
    void validateToken_whenTokenNotFound_throwsInvalid() {

        when(admissionTokenRepository.findByToken(TOKEN))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> admissionTokenService.validateToken(USER_ID, GAME_ID, TOKEN))
            .isInstanceOf(QueueTokenInvalidException.class);

        verify(admissionTokenRepository).findByToken(TOKEN);
    }

    @Test
    @DisplayName("만료 시간이 지난 ACTIVE 토큰을 검증하면 EXPIRED 상태가 되고 예외가 발생한다.")
    void validateToken_withExpiredActiveToken_marksExpiredAndThrowsExpired() {

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime issuedAt = now.minusMinutes(5);
        LocalDateTime expiredAt = now.minusMinutes(1);

        AdmissionToken admissionToken = activeToken(issuedAt, expiredAt);

        when(admissionTokenRepository.findByToken(TOKEN))
            .thenReturn(Optional.of(admissionToken));

        assertThatThrownBy(() -> admissionTokenService.validateToken(USER_ID, GAME_ID, TOKEN))
            .isInstanceOf(QueueTokenExpiredException.class);
        assertThat(admissionToken.getStatus())
            .isEqualTo(AdmissionTokenStatus.EXPIRED);

        verify(admissionTokenRepository).findByToken(TOKEN);
        verify(admissionTokenRepository, never()).findByTokenWithPessimisticWriteLock(TOKEN);
    }

    @Test
    @DisplayName("유효한 ACTIVE 토큰을 소비하면 USED 상태가 되고 사용 시간이 기록된다.")
    void consumeToken_withValidToken_marksUsed() {

        AdmissionToken admissionToken = activeToken();

        when(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN))
            .thenReturn(Optional.of(admissionToken));

        assertThatCode(() -> admissionTokenService.consumeToken(USER_ID, GAME_ID, TOKEN))
            .doesNotThrowAnyException();
        assertThat(admissionToken.getStatus())
            .isEqualTo(AdmissionTokenStatus.USED);
        assertThat(admissionToken.getUsedAt())
            .isNotNull();

        verify(admissionTokenRepository).findByTokenWithPessimisticWriteLock(TOKEN);
        verify(admissionTokenRepository, never()).findByToken(TOKEN);
    }
}
