package com.backtoback.reseat.domain.reservation.service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 선점(HOLD) 시간 정책의 단일 출처.
 * <p>
 * 불변식: HOLD_TTL >= PAYMENT_DEADLINE.
 * 선점 유지 시간이 결제 기한보다 짧으면, 결제가 성공하기 전에 좌석이 회수되어
 * "결제는 됐는데 좌석이 없는" 상태가 발생한다. (API 명세서 5.1 / 6.1)
 */
public final class HoldPolicy {

	/**
	 * 좌석 선점 유지 시간. 선점 성공 시각 + 이 값 = hold_expires_at.
	 */
	public static final Duration HOLD_TTL = Duration.ofMinutes(10);

	/**
	 * 주문 생성 후 결제 기한. 주문 생성 시각 + 이 값 = payment_deadline.
	 */
	public static final Duration PAYMENT_DEADLINE = Duration.ofMinutes(8);

	/**
	 * 선점 연장 상한(선점 10 + 결제 8). 좌석 점유가 무한히 늘어나는 것을 막는 가드.
	 */
	public static final Duration HOLD_EXTEND_CAP = Duration.ofMinutes(18);

	static {
		// 클래스 로딩 시 불변식을 강제한다. 상수를 잘못 바꾸면 부팅이 곧바로 실패한다(fail-fast).
		if (HOLD_TTL.compareTo(PAYMENT_DEADLINE) < 0) {
			throw new IllegalStateException(
				"불변식 위반: HOLD_TTL(" + HOLD_TTL.toMinutes() + "m) 은 "
					+ "PAYMENT_DEADLINE(" + PAYMENT_DEADLINE.toMinutes() + "m) 이상이어야 합니다.");
		}
	}

	private HoldPolicy() {
		// 인스턴스화 방지 (상수·정적 헬퍼 전용).
	}

	/**
	 * 선점 성공 시각 기준 만료 시각을 계산한다.
	 */
	public static LocalDateTime holdExpiresAt(LocalDateTime heldAt) {
		return heldAt.plus(HOLD_TTL);
	}
}
