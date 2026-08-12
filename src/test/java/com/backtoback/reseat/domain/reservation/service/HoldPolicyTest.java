package com.backtoback.reseat.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HoldPolicy 시간 정책")
class HoldPolicyTest {

	@Test
	@DisplayName("HOLD_TTL은 10분이다 (명세서 §5.1 정합)")
	void holdTtl_is10Minutes() {
		assertThat(HoldPolicy.HOLD_TTL).isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	@DisplayName("PAYMENT_DEADLINE은 8분이다 (명세서 §6.1 정합)")
	void paymentDeadline_is8Minutes() {
		assertThat(HoldPolicy.PAYMENT_DEADLINE).isEqualTo(Duration.ofMinutes(8));
	}

	@Test
	@DisplayName("불변식: HOLD_TTL >= PAYMENT_DEADLINE (깨지면 결제 성공 시점에 좌석이 풀림)")
	void invariant_holdTtl_gte_paymentDeadline() {
		assertThat(HoldPolicy.HOLD_TTL).isGreaterThanOrEqualTo(HoldPolicy.PAYMENT_DEADLINE);
	}

	@Test
	@DisplayName("연장 상한은 선점 10 + 결제 8 = 18분이다")
	void extendCap_is18Minutes() {
		assertThat(HoldPolicy.HOLD_EXTEND_CAP).isEqualTo(HoldPolicy.HOLD_TTL.plus(HoldPolicy.PAYMENT_DEADLINE));
	}

	@Test
	@DisplayName("holdExpiresAt(heldAt)은 선점 시각 + 10분을 돌려준다")
	void holdExpiresAt_addsTenMinutes() {
		LocalDateTime heldAt = LocalDateTime.of(2026, 7, 21, 14, 20, 0);

		LocalDateTime expiresAt = HoldPolicy.holdExpiresAt(heldAt);

		assertThat(expiresAt).isEqualTo(LocalDateTime.of(2026, 7, 21, 14, 30, 0));
	}
}
