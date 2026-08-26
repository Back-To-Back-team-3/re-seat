package com.backtoback.reseat.domain.queue.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.BDDMockito.*;

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
import com.backtoback.reseat.domain.queue.exception.QueueTokenAlreadyUsedException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenBrowsingExpiredException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenExpiredException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenInvalidException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenRequiredException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenRevokedException;
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
        LocalDateTime expiresAt = issuedAt.plusMinutes(21);

        return activeToken(issuedAt, expiresAt);
    }

    private AdmissionToken activeToken(LocalDateTime issuedAt, LocalDateTime expiresAt) {

        Game game = mock(Game.class);
        User user = mock(User.class);

        when(game.getId()).thenReturn(GAME_ID);
        when(user.getId()).thenReturn(USER_ID);

        LocalDateTime seatBrowsingExpiresAt = issuedAt.plusMinutes(3);

        return AdmissionToken.of(game, user, TOKEN, issuedAt, expiresAt, seatBrowsingExpiresAt);
    }

    @Test
    @DisplayName("사용자와 경기가 일치하는 ACTIVE 토큰은 검증에 성공한다.")
    void validateToken_withValidToken_succeeds() {

        // given
        AdmissionToken admissionToken = activeToken();
        given(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN))
            .willReturn(Optional.of(admissionToken));

        // when & then
        assertThatCode(() -> admissionTokenService.validateToken(USER_ID, GAME_ID, TOKEN)).doesNotThrowAnyException();

        // then
        assertThat(admissionToken.getStatus()).isEqualTo(AdmissionTokenStatus.ACTIVE);
        then(admissionTokenRepository).should().findByTokenWithPessimisticWriteLock(TOKEN);
        then(admissionTokenRepository).should(never()).findByToken(TOKEN);
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

        // given
        given(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> admissionTokenService.validateToken(USER_ID, GAME_ID, TOKEN))
            .isInstanceOf(QueueTokenInvalidException.class);

        // then
        then(admissionTokenRepository).should().findByTokenWithPessimisticWriteLock(TOKEN);
        then(admissionTokenRepository).should(never()).findByToken(TOKEN);
    }

    @Test
    @DisplayName("만료 시간이 지난 ACTIVE 토큰을 검증하면 EXPIRED 상태가 되고 예외가 발생한다.")
    void validateToken_withExpiredActiveToken_marksExpiredAndThrowsExpired() {

        // given
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime issuedAt = now.minusMinutes(5);
        LocalDateTime expiredAt = now.minusMinutes(1);

        AdmissionToken admissionToken = activeToken(issuedAt, expiredAt);

        given(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN))
            .willReturn(Optional.of(admissionToken));

        // when & then
        assertThatThrownBy(() -> admissionTokenService.validateToken(USER_ID, GAME_ID, TOKEN))
            .isInstanceOf(QueueTokenExpiredException.class);

        // then
        assertThat(admissionToken.getStatus()).isEqualTo(AdmissionTokenStatus.EXPIRED);
        then(admissionTokenRepository).should().findByTokenWithPessimisticWriteLock(TOKEN);
        then(admissionTokenRepository).should(never()).findByToken(TOKEN);
    }

    @Test
    @DisplayName("유효한 ACTIVE 토큰을 소비하면 USED 상태가 되고 사용 시간이 기록된다.")
    void consumeToken_withValidToken_marksUsed() {

        AdmissionToken admissionToken = activeToken();

        when(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN))
            .thenReturn(Optional.of(admissionToken));

        assertThatCode(() -> admissionTokenService.consumeToken(USER_ID, GAME_ID, TOKEN)).doesNotThrowAnyException();
        assertThat(admissionToken.getStatus()).isEqualTo(AdmissionTokenStatus.USED);
        assertThat(admissionToken.getUsedAt()).isNotNull();

        verify(admissionTokenRepository).findByTokenWithPessimisticWriteLock(TOKEN);
        verify(admissionTokenRepository, never()).findByToken(TOKEN);
    }

    @Test
    @DisplayName("ACTIVE 토큰을 반복 검증해도 상태와 만료시간이 유지된다.")
    void validateToken_repeatedly_keepsStatusAndExpiration() {

        // given
        // 좌석 조회와 재시도에서 동일 토큰을 반복 검증해도 최초 유효시간을 유지하는 조건을 준비한다.
        AdmissionToken activeToken = activeToken();
        given(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN)).willReturn(Optional.of(activeToken));

        LocalDateTime expiresAt = activeToken.getExpiresAt();
        LocalDateTime seatBrowsingExpiresAt = activeToken.getSeatBrowsingExpiresAt();

        // when
        // 실제 반복 조회 상황을 재현하기 위해 같은 사용자 · 경기 · 토큰으로 두 번 검증한다.
        assertThatCode(() -> admissionTokenService.validateToken(USER_ID, GAME_ID, TOKEN)).doesNotThrowAnyException();
        assertThatCode(() -> admissionTokenService.validateToken(USER_ID, GAME_ID, TOKEN)).doesNotThrowAnyException();

        // then
        // 검증은 토큰을 소비하지 않으며 전체 · 탐색 만료시간도 새로 계산하지 않아야 한다.
        assertThat(activeToken.getStatus()).isEqualTo(AdmissionTokenStatus.ACTIVE);
        assertThat(activeToken.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(activeToken.getSeatBrowsingExpiresAt()).isEqualTo(seatBrowsingExpiresAt);

        then(admissionTokenRepository).should(times(2)).findByTokenWithPessimisticWriteLock(TOKEN);
    }

    @Test
    @DisplayName("좌석 탐색 시간이 지난 ACTIVE 토큰을 검증하면 BROWSING_EXPIRED 상태가 되고 예외가 발생한다.")
    void validateToken_withBrowsingExpiredActiveToken_marksBrowsingExpiredAndThrows() {

        // given
        // 전체 유효시간은 남았지만 최초 좌석 탐색 제한 3분만 지난 ACTIVE 토큰을 준비한다.
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime issuedAt = now.minusMinutes(5);
        LocalDateTime expiresAt = now.plusMinutes(1);

        AdmissionToken activeToken = activeToken(issuedAt, expiresAt);

        given(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN)).willReturn(Optional.of(activeToken));

        // when & then
        // 전체 만료보다 탐색 만료를 먼저 구분하여 BROWSING_EXPIRED 예외를 반환해야 한다.
        assertThatThrownBy(() -> admissionTokenService.validateToken(USER_ID, GAME_ID, TOKEN))
            .isInstanceOf(QueueTokenBrowsingExpiredException.class);

        // then
        // 탐색 만료 상태로 전환해야 이후 요청에서 토큰 사용을 차단하고 같은 경기의 재진입을 허용할 수 있다.
        assertThat(activeToken.getStatus()).isEqualTo(AdmissionTokenStatus.BROWSING_EXPIRED);

        then(admissionTokenRepository).should().findByTokenWithPessimisticWriteLock(TOKEN);
        then(admissionTokenRepository).should(never()).findByToken(TOKEN);
    }

    @Test
    @DisplayName("REVOKED 토큰을 검증하면 예외가 발생하고 취소 상태가 유지된다.")
    void validateToken_withRevokedToken_throwsRevoked() {

        // given
        // 대기 취소로 REVOKED가 된 토큰이 좌석 조회 · 선점 단계의 검증 요청에 전달된 상황을 준비한다.
        AdmissionToken revokedToken = activeToken();
        revokedToken.revoke();

        given(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN))
            .willReturn(Optional.of(revokedToken));

        // when & then
        // 취소된 토큰은 현재 상태를 유지하고 좌석 조회 · 선점 단계에서도 즉시 거부해야 한다.
        assertThatThrownBy(() -> admissionTokenService.validateToken(USER_ID, GAME_ID, TOKEN))
            .isInstanceOf(QueueTokenRevokedException.class);

        // then
        // 검증 실패는 토큰을 다시 ACTIVE로 변경하거나 소비하지 않아야 한다.
        assertThat(revokedToken.getStatus()).isEqualTo(AdmissionTokenStatus.REVOKED);
        assertThat(revokedToken.getUsedAt()).isNull();

        // 검증 시에도 상태 확인이 필요한 비관적 락 조회 경로만 사용했는지 확인한다.
        then(admissionTokenRepository).should().findByTokenWithPessimisticWriteLock(TOKEN);
        then(admissionTokenRepository).should(never()).findByToken(TOKEN);
    }

    @Test
    @DisplayName("USED 토큰을 검증하면 이미 사용된 토큰 예외가 발생하고 최초 사용 시간이 유지된다.")
    void validateToken_withUsedToken_throwsAlreadyUsed() {

        // given
        // 이미 소비된 Queue-Token이 좌석 조회나 재선점 단계에서 다시 사용되는 상황을 준비한다.
        AdmissionToken usedToken = activeToken();
        LocalDateTime firstUsedAt = LocalDateTime.now();
        usedToken.use(firstUsedAt);
        given(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN)).willReturn(Optional.of(usedToken));

        // when & then
        assertThatThrownBy(() -> admissionTokenService.validateToken(USER_ID, GAME_ID, TOKEN))
            .isInstanceOf(QueueTokenAlreadyUsedException.class);

        // then
        // USED 토큰의 재검증은 최초 사용시간과 상태를 변경하지 않아야 한다.
        assertThat(usedToken.getStatus()).isEqualTo(AdmissionTokenStatus.USED);
        assertThat(usedToken.getUsedAt()).isEqualTo(firstUsedAt);

        then(admissionTokenRepository).should().findByTokenWithPessimisticWriteLock(TOKEN);
        then(admissionTokenRepository).should(never()).findByToken(TOKEN);
    }

    @Test
    @DisplayName("REVOKED 토큰을 소비하면 예외가 발생하고 사용 시간이 기록되지 않는다.")
    void consumeToken_withRevokedToken_throwsRevoked() {

        // given
        // 대기 취소로 REVOKED가 된 토큰이 티켓 발급 단계의 소비 요청에 전달된 상황을 준비한다.
        AdmissionToken revokedToken = activeToken();
        revokedToken.revoke();

        given(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN))
            .willReturn(Optional.of(revokedToken));

        // when & then
        // 취소된 토큰은 USED로 전이하거나 사용시간을 기록하지 않고 즉시 거부해야 한다.
        assertThatThrownBy(() -> admissionTokenService.consumeToken(USER_ID, GAME_ID, TOKEN))
            .isInstanceOf(QueueTokenRevokedException.class);

        // then
        assertThat(revokedToken.getStatus()).isEqualTo(AdmissionTokenStatus.REVOKED);

        // 취소된 Queue-Token은 티켓 발급 단계에서도 소비되지 않아야 한다.
        assertThat(revokedToken.getUsedAt()).isNull();

        // 소비 경합을 막는 비관적 락 조회 경로만 사용했는지 확인한다.
        then(admissionTokenRepository).should().findByTokenWithPessimisticWriteLock(TOKEN);
        then(admissionTokenRepository).should(never()).findByToken(TOKEN);
    }

    @Test
    @DisplayName("EXPIRED 토큰을 소비하면 예외가 발생하고 사용 시간이 기록되지 않는다.")
    void consumeToken_withExpiredToken_throwsExpired() {

        // given
        // 전체 유효시간이 끝난 토큰을 EXPIRED로 확정한 뒤 티켓 발급 단계에 전달된 상황을 준비한다.
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime issuedAt = now.minusMinutes(5);
        LocalDateTime expiresAt = now.minusMinutes(1);

        AdmissionToken expiredToken = activeToken(issuedAt, expiresAt);
        expiredToken.expire(now);

        given(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN))
            .willReturn(Optional.of(expiredToken));

        // when & then
        // 만료 토큰은 USED로 전이하거나 사용시간을 기록하지 않고 소비를 거부해야 한다.
        assertThatThrownBy(() -> admissionTokenService.consumeToken(USER_ID, GAME_ID, TOKEN))
            .isInstanceOf(QueueTokenExpiredException.class);

        // then
        assertThat(expiredToken.getStatus()).isEqualTo(AdmissionTokenStatus.EXPIRED);

        // 전체 유효시간이 끝난 Queue-Token은 티켓 발급 단계에서 소비되지 않아야 한다.
        assertThat(expiredToken.getUsedAt()).isNull();

        // 소비 경합을 막는 비관적 락 조회 경로만 사용했는지 확인한다.
        then(admissionTokenRepository).should().findByTokenWithPessimisticWriteLock(TOKEN);
        then(admissionTokenRepository).should(never()).findByToken(TOKEN);
    }

    @Test
    @DisplayName("USED 토큰을 다시 소비하면 이미 사용된 토큰 예외가 발생하고 최초 사용 시간이 유지된다.")
    void consumeToken_withUsedToken_throwsAlreadyUsed() {

        // given
        // 이미 소비된 Queue-Token이 티켓 발급 단계에서 다시 소비되는 상황을 준비한다.
        AdmissionToken usedToken = activeToken();
        LocalDateTime firstUsedAt = LocalDateTime.now();
        usedToken.use(firstUsedAt);
        given(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN)).willReturn(Optional.of(usedToken));

        // when & then
        assertThatThrownBy(() -> admissionTokenService.consumeToken(USER_ID, GAME_ID, TOKEN))
            .isInstanceOf(QueueTokenAlreadyUsedException.class);

        // then
        // 중복 소비는 상태와 최초 사용시간을 변경하지 않아야 한다.
        assertThat(usedToken.getStatus()).isEqualTo(AdmissionTokenStatus.USED);
        assertThat(usedToken.getUsedAt()).isEqualTo(firstUsedAt);

        // 소비 경합을 막는 비관적 락 조회 경로만 사용했는지 확인한다.
        then(admissionTokenRepository).should().findByTokenWithPessimisticWriteLock(TOKEN);
        then(admissionTokenRepository).should(never()).findByToken(TOKEN);
    }

    @Test
    @DisplayName("다른 사용자의 Queue-Token을 검증하면 유효하지 않은 토큰 예외가 발생한다.")
    void validateToken_withDifferentUser_throwsInvalid() {

        // given
        // 사용자 불일치에서 검증이 종료되므로 경기 ID는 Stub으로 설정하지 않는다.
        Game game = mock(Game.class);
        User user = mock(User.class);

        when(user.getId()).thenReturn(USER_ID);

        LocalDateTime issuedAt = LocalDateTime.now().minusMinutes(1);
        LocalDateTime expiresAt = issuedAt.plusMinutes(21);
        LocalDateTime seatBrowsingExpiresAt = issuedAt.plusMinutes(3);

        AdmissionToken activeToken = AdmissionToken.of(game, user, TOKEN, issuedAt, expiresAt, seatBrowsingExpiresAt);

        // 다른 사용자가 유효한 토큰 값을 이용해 검증을 요청한 상황을 준비한다.
        Long differentUserId = 2L;

        given(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN)).willReturn(Optional.of(activeToken));

        // when & then
        // 토큰 소유자가 요청 사용자와 다르면 토큰의 현재 상태를 노출하지 않고 유효하지 않은 토큰으로 거부해야 한다.
        assertThatThrownBy(() -> admissionTokenService.validateToken(differentUserId, GAME_ID, TOKEN))
            .isInstanceOf(QueueTokenInvalidException.class);

        // then
        // 사용자 불일치 요청은 토큰 상태를 변경하지 않아 정상 소유자의 이후 예매 흐름에 영향을 주지 않아야 한다.
        assertThat(activeToken.getStatus()).isEqualTo(AdmissionTokenStatus.ACTIVE);

        then(admissionTokenRepository).should().findByTokenWithPessimisticWriteLock(TOKEN);
        then(admissionTokenRepository).should(never()).findByToken(TOKEN);
    }

    @Test
    @DisplayName("다른 경기의 Queue-Token을 검증하면 유효하지 않은 토큰 예외가 발생한다.")
    void validateToken_withDifferentGame_throwsInvalid() {

        // given
        // 사용자는 같지만 토큰 발급 경기와 검증 요청 경기가 다른 상황을 준비한다.
        AdmissionToken activeToken = activeToken();
        Long differentGameId = 2L;

        given(admissionTokenRepository.findByTokenWithPessimisticWriteLock(TOKEN)).willReturn(Optional.of(activeToken));

        // when & then
        // Queue-Token은 발급된 경기에서만 사용할 수 있으므로 다른 경기 요청을 유효하지 않은 토큰으로 거부해야 한다.
        assertThatThrownBy(() -> admissionTokenService.validateToken(USER_ID, differentGameId, TOKEN))
            .isInstanceOf(QueueTokenInvalidException.class);

        // then
        // 경기 불일치 요청도 기존 토큰 상태를 변경하지 않아 원래 경기의 예매 흐름을 유지해야 한다.
        assertThat(activeToken.getStatus()).isEqualTo(AdmissionTokenStatus.ACTIVE);

        then(admissionTokenRepository).should().findByTokenWithPessimisticWriteLock(TOKEN);
        then(admissionTokenRepository).should(never()).findByToken(TOKEN);
    }
}
