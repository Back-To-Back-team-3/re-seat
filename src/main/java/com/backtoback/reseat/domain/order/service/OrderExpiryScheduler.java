package com.backtoback.reseat.domain.order.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 기한 만료 주문 처리 스케줄러.
 *
 * <p>스케줄러는 만료 판정 기준 시간을 생성하고 OrderExpiryService를 호출하며 결과를 로깅한다.
 * 상태 전이와 트랜잭션 경계는 서비스에서 처리한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpiryScheduler {

	private final OrderExpiryService orderExpiryService;

	/**
	 * 결제 기한이 지난 주문과 연결된 선점을 주기적으로 만료 처리한다.
	 */
	@Scheduled(fixedDelay = 10_000, initialDelay = 30_000)
	public void sweepExpiredOrders() {

		LocalDateTime now = LocalDateTime.now();
		OrderExpiryService.OrderExpiryResult result = orderExpiryService.expireOrders(now);

		// 처리 건수가 있을 때만 INFO로 기록하고 반복되는 0건 결과는 DEBUG로 남긴다.
		if (result.total() > 0) {
			log.info("[OrderExpiry] 만료 선점 회수 완료 - 주문 {}건 EXPIRED, 예약 {}건 EXPIRED, 좌석 {}건 AVAILABLE",
				result.expiredOrders(), result.expiredReservations(), result.releasedSeats());
		} else {
			log.debug("[OrderExpiry] 만료 선점 없음 (기준 시각: {})", now);
		}
	}
}
