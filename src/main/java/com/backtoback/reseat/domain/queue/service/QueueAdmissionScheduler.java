package com.backtoback.reseat.domain.queue.service;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 경기별 대기열의 사용자를 주기적으로 입장 허용 처리하는 스케줄러
 *
 * <p>Redis SCAN으로 경기별 대기열 키를 조회하고,
 * 기존 입장 허용 서비스를 호출해 대기열 앞 사용자의 입장 토큰을 발급한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

	private final RedisTemplate<String, String> redisTemplate;
	private final AdmissionTokenService admissionTokenService;

	/**
	 * Redis에 존재하는 경기별 대기열을 조회하고 앞 순서 사용자의 입장 처리를 요청한다,
	 *
	 * <p>fixedDelay는 이전 작업이 시작된 시점이 아니라 완전히 종료된 시점부터 다음 실행 간격을 계산한다.
	 * 최초 실행도 애플리케이션이 시작 직후가 아니라 동일한 시간만큼 기다린 후 시작한다.</p>
	 */
	@Scheduled(fixedDelay = QueueAdmissionPolicy.ADMISSION_INTERVAL_MILLIS, initialDelay = QueueAdmissionPolicy.ADMISSION_INTERVAL_MILLIS)
	public void admitWaitingUsers() {

		// Redis 전체 키를 한 번에 조회하는 KEYS 대신 SCAN을 사용하여 서버 부하를 줄인다.
		// queue:game:* 패턴에 해당하는 경기별 대기열 키만 점진적으로 조회한다.
		// count는 전체 조회 개수의 제한이 아니라 Redis에 전달하는 예상 조회량에 대한 힌트다.
		// 실제 한 번의 SCAN에서 반환되는 Key 개수는 count와 다를 수 있다.
		ScanOptions scanOptions = ScanOptions.scanOptions()
			.match(redisKeyPattern())
			.count(100)
			.build();

		// SCAN Cursor가 사용한 Redis 연결이 작업 종료 후  반드시 반환되도록 try-with-resources로 관리한다.
		try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
			// 조회한 경기별 대기열을 순차적으로 처리하며, 각 경기의 입장 처리는 admitQueue에서 개별적으로 보호한다.
			while (cursor.hasNext()) {
				admitQueue(cursor.next());
			}
		}
	}

	// 경기별 Redis 대기열을 조회하기 위한 검색 패턴을 반환한다.
	private String redisKeyPattern() {
		return "queue:game:*";
	}

	// 경기별 Redis 대기열 Key에서 경기 ID를 추출한다.
	private Long parseGameId(String redisKey) {
		// queue:game: 접두사 이후의 값을 경기 ID로 사용한다.
		String prefix = "queue:game:";
		return Long.parseLong(redisKey.substring(prefix.length()));
	}

	// 특정 경기 대기열의 입장 허용 처리를 요청한다.
	private void admitQueue(String redisKey) {

		// 특정 경기의 처리 실패가 다른 경기 대기열의 자동 입장 처리까지 중단시키지 않도록 개별적으로 예외를 처리한다.
		try {
			Long gameId = parseGameId(redisKey);
			// Redis 대기 순서가 앞선 사용자부터 설정된 최대 대상 수만큼 입장 처리를 위임한다.
			admissionTokenService.admit(gameId, QueueAdmissionPolicy.ADMIT_LIMIT);
		}
		// 잘못된 Redis Key 또는 입장 처리 중 발생한 예외를 기록하고 다음 경기 대기열 처리를 계속한다.
		catch (RuntimeException e) {
			// 인터럽트가 발생한 경우 현재 스케줄 실행을 즉시 종료하도록 예외를 다시 전파한다.
			if (Thread.currentThread().isInterrupted()) {
				throw e;
			}
			log.error(
				"대기열 자동 입장 처리 실패. redisKey={}",
				redisKey, e);
		}
	}
}
