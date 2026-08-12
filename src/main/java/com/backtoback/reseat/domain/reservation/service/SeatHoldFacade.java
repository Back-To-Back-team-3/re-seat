package com.backtoback.reseat.domain.reservation.service;

import org.springframework.stereotype.Component;

import com.backtoback.reseat.domain.queue.service.AdmissionTokenService;
import com.backtoback.reseat.domain.reservation.dto.request.SeatHoldRequest;
import com.backtoback.reseat.domain.reservation.dto.response.ReservationResponse;
import com.backtoback.reseat.domain.reservation.service.lock.SeatLockStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 좌석 선점 흐름을 조율하는 Facade.
 * <p>트랜잭션 경계 바깥에서 락을 소유하고,
 * 내부에서 ReservationService holdSeats의 트랜잭션이 커밋된 뒤 락을 해제한다.</p>
 * <p>@Transactional 금지 — Facade에 트랜잭션을 걸면 커밋 전에 락이 해제돼 over-booking이 재발한다.</p>
 * <p>흐름:</p>
 *
 * <pre>
 * 1. validateToken  — 불필요한 락 획득 방지 (락 전 사전 검증)
 * 2. executeWithLocks → holdSeats (트랜잭션·커밋)
 * 3. consumeToken   — 선점 성공 확인 후 토큰 USED 전이
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatHoldFacade {

	private final ReservationService reservationService;
	private final SeatLockStrategy seatLockStrategy;
	private final AdmissionTokenService admissionTokenService;

	/**
	 * 좌석 선점 요청을 처리한다.
	 * Queue-Token 검증 → 분산 락 획득 → 좌석 선점 트랜잭션 → 토큰 소비 순으로 처리한다.
	 *
	 * @param userId 인증 사용자 ID
	 * @param token Queue-Token 헤더 값
	 * @param request 선점 요청 DTO
	 * @return 선점 결과 응답 DTO
	 */
	public ReservationResponse holdSeats(Long userId, String token, SeatHoldRequest request) {
		// 1단계: 락 획득 전 토큰 사전 검증 — 유효하지 않은 요청이 락까지 진입하는 것을 차단
		admissionTokenService.validateToken(userId, request.gameId(), token);

		// 2단계: 좌석 단위 분산 락 획득 → 선점 트랜잭션 실행(커밋) → 락 해제
		ReservationResponse response
		    = seatLockStrategy
		        .executeWithLocks(request.gameSeatIds(), () -> reservationService.holdSeats(userId, request));

		// 3단계: 선점 성공 후 토큰 소비 — 동일 토큰으로 재진입 방지
		try {
			admissionTokenService.consumeToken(userId, request.gameId(), token);
		} catch (Exception e) {
			// consumeToken 실패 시 선점은 롤백하지 않는다.
			// Queue-Token TTL(5분) 내 재진입 가능하나, validateToken에서 ACTIVE 상태·만료 여부를 재검증해 차단된다.
			// 운영 모니터링 대상으로 ERROR 로그를 남긴다.
			log.error("[SeatHoldFacade] 토큰 소비 실패 — 선점은 유지됩니다. userId={}, gameId={}", userId, request.gameId(), e);
		}

		log
		    .info(
		        "[SeatHoldFacade] 좌석 선점 완료. userId={}, gameId={}, seats={}",
		        userId,
		        request.gameId(),
		        request.gameSeatIds()
		    );

		return response;
	}
}
