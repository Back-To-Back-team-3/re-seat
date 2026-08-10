import http from 'k6/http';
import {check} from 'k6';
import {Counter, Rate} from 'k6/metrics';


// C(선점) 파트 부하 테스트 — 시나리오 A: 동일 좌석 경합
// 측정 목적: N명이 동일한 gameSeatId로 동시 선점 요청 시 SEAT_ALREADY_HELD 비율 및 TPS 측정

// 커스텀 메트릭 정의
const seatAlreadyHeldCounter = new Counter('seat_already_held_count');
const tokenAlreadyUsedCounter = new Counter('token_already_used_count');
const lockFailedCounter = new Counter('lock_failed_count');
const unexpectedCounter = new Counter('unexpected_error_count');
const reservationSuccessRate = new Rate('reservation_success_rate');
const serverErrorRate = new Rate('server_error_rate');

export const options = {
	scenarios: {
		concurrent_same_seat: {
			executor: 'per-vu-iterations', // 모든 VU 동시 출발
			vus: __ENV.VUS ? parseInt(__ENV.VUS) : 50,
			iterations: 1,
			maxDuration: '30s',
		},
	},
	thresholds: {
		http_req_duration: ['p(99)<2000'],   // p99 지연 2초 이하
		server_error_rate: ['rate<0.01'],    // 5xx 서버 에러율 1% 미만
	},
};

// 환경 변수 설정
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_TOKEN = __ENV.JWT_TOKEN || 'DUMMY_JWT_TOKEN';
const QUEUE_TOKEN = __ENV.QUEUE_TOKEN || 'DUMMY_QUEUE_TOKEN';
const GAME_ID = __ENV.GAME_ID ? parseInt(__ENV.GAME_ID) : 1;
const TARGET_SEAT_ID = __ENV.TARGET_SEAT_ID ? parseInt(__ENV.TARGET_SEAT_ID) : 1;

export default function () {
	const res = http.post(
			`${BASE_URL}/api/v1/reservations`,
			JSON.stringify({gameId: GAME_ID, gameSeatIds: [TARGET_SEAT_ID]}),
			{
				headers: {
					'Content-Type': 'application/json',
					'Authorization': `Bearer ${JWT_TOKEN}`,
					'Queue-Token': QUEUE_TOKEN,
				},
				responseCallback: http.expectedStatuses(201, 409),
			}
	);

	// 응답 에러코드 파싱
	let errorCode = '';
	try {
		errorCode = JSON.parse(res.body).errorCode || '';
	} catch (_) {
	}

	serverErrorRate.add(res.status >= 500 ? 1 : 0);

	// 에러코드별 카운터 집계
	if (res.status === 201) {
		reservationSuccessRate.add(1);
	} else if (errorCode === 'SEAT_ALREADY_HELD') {
		reservationSuccessRate.add(0);
		seatAlreadyHeldCounter.add(1);
	} else if (errorCode === 'QUEUE_TOKEN_ALREADY_USED') {
		reservationSuccessRate.add(0);
		tokenAlreadyUsedCounter.add(1);
	} else if (errorCode === 'LOCK_FAILED') {
		reservationSuccessRate.add(0);
		lockFailedCounter.add(1);
	} else {
		reservationSuccessRate.add(0);
		unexpectedCounter.add(1);
	}

	// 응답 상태 검증
	check(res, {
		'response is expected': (r) =>
				r.status === 201 ||
				(r.status === 409 && [
					'SEAT_ALREADY_HELD',
					'QUEUE_TOKEN_ALREADY_USED',
					'LOCK_FAILED',
				].includes(errorCode)),
	});
}
