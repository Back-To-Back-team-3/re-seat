package com.backtoback.reseat.domain.queue.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.backtoback.reseat.domain.game.entity.Game;
import com.backtoback.reseat.domain.queue.exception.QueueInvalidStatusException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenAlreadyUsedException;
import com.backtoback.reseat.domain.queue.exception.QueueTokenExpiredException;
import com.backtoback.reseat.domain.user.entity.User;

@DisplayName("AdmissionToken 상태 전이")
public class AdmissionTokenTest {

	private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 7, 23, 1, 0);
	private static final LocalDateTime EXPIRES_AT = ISSUED_AT.plusMinutes(5);
	private static final String TOKEN = "qt_test";

	private AdmissionToken activeToken() {
		return AdmissionToken.of(mock(Game.class), mock(User.class), TOKEN, ISSUED_AT, EXPIRES_AT);
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
		AdmissionToken admissionToken = activeToken();
		LocalDateTime expiredAt = EXPIRES_AT.minusSeconds(1);

		assertThatThrownBy(() -> admissionToken.expire(expiredAt)).isInstanceOf(QueueInvalidStatusException.class);
		assertThat(admissionToken.getStatus()).isEqualTo(AdmissionTokenStatus.ACTIVE);
	}

	@Test
	@DisplayName("USED 토큰을 만료 전에 재활성화하면 ACTIVE 상태가 되고 사용 시간이 초기화된다.")
	void reactivate_beforeExpiration_changesStatusToActive() {

		// given
		AdmissionToken admissionToken = activeToken();
		LocalDateTime usedAt = ISSUED_AT.plusMinutes(1);
		admissionToken.use(usedAt);
		LocalDateTime reactivationAt = ISSUED_AT.plusMinutes(2);

		// when
		admissionToken.reactivate(reactivationAt);

		// then
		// 기존 Queue-Token과 발급 · 만료 시간은 유지하고 사용 시간만 초기화한다.
		assertThat(admissionToken.getStatus()).isEqualTo(AdmissionTokenStatus.ACTIVE);
		assertThat(admissionToken.getUsedAt()).isNull();
		assertThat(admissionToken.getToken()).isEqualTo(TOKEN);
		assertThat(admissionToken.getIssuedAt()).isEqualTo(ISSUED_AT);
		assertThat(admissionToken.getExpiresAt()).isEqualTo(EXPIRES_AT);
	}
}
