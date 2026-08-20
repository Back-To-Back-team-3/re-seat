package com.backtoback.reseat.domain.queue.entity;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.queue.exception.QueueTokenAlreadyUsedException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenExpiredException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenNotExpiredException;
import com.backtoback.reseat.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("AdmissionToken 상태 전이")
public class AdmissionTokenTest {

    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 7, 23, 1, 0);
    private static final LocalDateTime EXPIRES_AT = ISSUED_AT.plusMinutes(21);
    private static final LocalDateTime SEAT_BROWSING_EXPIRES_AT = ISSUED_AT.plusMinutes(3);
    private static final String TOKEN = "qt_test";

    private AdmissionToken activeToken() {
        return AdmissionToken
            .of(mock(Game.class), mock(User.class), TOKEN, ISSUED_AT, EXPIRES_AT, SEAT_BROWSING_EXPIRES_AT);
    }

    @Test
    @DisplayName("ACTIVE 토큰을 만료 전에 사용하면 USED 상태가 되고 사용시 시간이 기록된다.")
    void use_beforeExpiration_changesStatusToUsed() {
        AdmissionToken admissionToken = activeToken();
        LocalDateTime usedAt = EXPIRES_AT.minusMinutes(1);

        admissionToken.use(usedAt);

        assertThat(admissionToken.getStatus()).isEqualTo(AdmissionTokenStatus.USED);
        assertThat(admissionToken.getUsedAt()).isEqualTo(usedAt);
    }

    @Test
    @DisplayName("ACTIVE 토큰을 만료 시간에 사용하면 예외가 발생한다.")
    void use_atExpiration_throws() {
        AdmissionToken admissionToken = activeToken();

        assertThatThrownBy(() -> admissionToken.use(EXPIRES_AT)).isInstanceOf(QueueTokenExpiredException.class);
        assertThat(admissionToken.getStatus()).isEqualTo(AdmissionTokenStatus.ACTIVE);
    }

    @Test
    @DisplayName("ACTIVE 토큰을 만료 시간 이후에 사용하면 예외가 발생한다.")
    void use_afterExpiration_throws() {
        AdmissionToken admissionToken = activeToken();
        LocalDateTime usedAt = EXPIRES_AT.plusSeconds(1);

        assertThatThrownBy(() -> admissionToken.use(usedAt)).isInstanceOf(QueueTokenExpiredException.class);
        assertThat(admissionToken.getStatus()).isEqualTo(AdmissionTokenStatus.ACTIVE);
    }

    @Test
    @DisplayName("이미 사용된 토큰을 다시 사용하면 예외가 발생하고 최초 사용 시간이 유지된다.")
    void use_fromUsed_throws() {
        AdmissionToken admissionToken = activeToken();
        LocalDateTime firstUsedAt = EXPIRES_AT.minusMinutes(2);
        LocalDateTime secondUseAt = EXPIRES_AT.minusMinutes(1);

        admissionToken.use(firstUsedAt);

        assertThatThrownBy(() -> admissionToken.use(secondUseAt)).isInstanceOf(QueueTokenAlreadyUsedException.class);
        assertThat(admissionToken.getStatus()).isEqualTo(AdmissionTokenStatus.USED);
        assertThat(admissionToken.getUsedAt()).isEqualTo(firstUsedAt);
    }

    @Test
    @DisplayName("ACTIVE 토큰을 만료 시간에 만료 처리하면 EXPIRED 상태가 된다.")
    void expire_atExpiration_changesStatusToExpired() {
        AdmissionToken admissionToken = activeToken();

        admissionToken.expire(EXPIRES_AT);

        assertThat(admissionToken.getStatus()).isEqualTo(AdmissionTokenStatus.EXPIRED);
    }

    @Test
    @DisplayName("ACTIVE 토큰을 만료 시간 전에 만료 처리하면 예외가 발생한다.")
    void expire_beforeExpiration_throws() {

        // given
        AdmissionToken admissionToken = activeToken();
        LocalDateTime expiredAt = EXPIRES_AT.minusSeconds(1);

        // when & then
        assertThatThrownBy(() -> admissionToken.expire(expiredAt)).isInstanceOf(QueueTokenNotExpiredException.class);

        // then
        assertThat(admissionToken.getStatus()).isEqualTo(AdmissionTokenStatus.ACTIVE);
    }

    @Test
    @DisplayName("최초 좌석 탐색 완료를 반복 처리해도 최초 완료시간과 만료시간이 유지된다.")
    void completeSeatBrowsing_twice_keepsFirstCompletionAndExpiration() {

        // given
        // 최초 선점과 재선점이 연속으로 발생해도 최초 탐색 완료시간과 두 만료시간을 유지하는 조건을 준비한다.
        AdmissionToken activeToken = activeToken();
        LocalDateTime firstSeatBrowsingCompletedAt = SEAT_BROWSING_EXPIRES_AT.minusMinutes(1);
        LocalDateTime secondSeatBrowsingCompletedAt = firstSeatBrowsingCompletedAt.plusSeconds(1);
        LocalDateTime expiresAt = activeToken.getExpiresAt();
        LocalDateTime seatBrowsingExpiresAt = activeToken.getSeatBrowsingExpiresAt();

        // when
        // 첫 호출에서만 탐색 완료시간을 기록하고, 재선점에 해당하는 두 번째 호출은 기존 완료정보를 유지해야 한다.
        activeToken.completeSeatBrowsing(firstSeatBrowsingCompletedAt);
        activeToken.completeSeatBrowsing(secondSeatBrowsingCompletedAt);

        // then
        // 재선점은 Queue-Token 상태나 유효시간을 변경하지 않고 최초 탐색 완료시간을 유지해야 한다.
        assertThat(activeToken.getStatus()).isEqualTo(AdmissionTokenStatus.ACTIVE);
        assertThat(activeToken.getSeatBrowsingCompletedAt()).isEqualTo(firstSeatBrowsingCompletedAt);
        assertThat(activeToken.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(activeToken.getSeatBrowsingExpiresAt()).isEqualTo(seatBrowsingExpiresAt);
    }
}
