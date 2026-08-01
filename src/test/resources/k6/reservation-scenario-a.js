import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';


// C(선점) 파트 부하 테스트 - 시나리오 A: 동일 좌석 경합
// 측정 목적: N명이 동일한 gameSeatId로 동시 선점 요청 시 LOCK_FAILED 비율 및 TPS 측정


// 커스텀 메트릭 정의
const lockFailedCounter = new Counter('lock_failed_count');
const reservationSuccessRate = new Rate('reservation_success_rate');

export const options = {
    scenarios: {
        concurrent_same_seat: {
            executor: 'per-vu-iterations',
            vus: __ENV.VUS ? parseInt(__ENV.VUS) : 50, // 동시 요청 VU 수
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        http_req_duration: ['p(99)<2000'], // p99 지연 2초 이하 (기획서 11.3)
        http_req_failed: ['rate<0.01'],    // 5xx 서버 에러율 1% 미만 (기획서 11.3)
    },
};

// 환경 변수 설정
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_TOKEN = __ENV.JWT_TOKEN || 'DUMMY_JWT_TOKEN';
const QUEUE_TOKEN = __ENV.QUEUE_TOKEN || 'DUMMY_QUEUE_TOKEN';
const GAME_ID = __ENV.GAME_ID ? parseInt(__ENV.GAME_ID) : 1;
const TARGET_SEAT_ID = __ENV.TARGET_SEAT_ID ? parseInt(__ENV.TARGET_SEAT_ID) : 100;

export default function () {
    const url = `${BASE_URL}/api/v1/reservations`;

    const payload = JSON.stringify({
        gameId: GAME_ID,
        gameSeatIds: [TARGET_SEAT_ID], // 모든 VU가 동일한 좌석 선점 시도
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${JWT_TOKEN}`,
            'Queue-Token': QUEUE_TOKEN,
        },
    };

    const res = http.post(url, payload, params);

    // 응답 상태 검증 (201 성공 또는 락 충돌/선점 실패)
    const isSuccess = check(res, {
        'status is 201 (Success)': (r) => r.status === 201,
        'status is 400 or 409 (Lock Failed / Already Reserved)': (r) => r.status === 400 || r.status === 409,
    });

    if (res.status === 201) {
        reservationSuccessRate.add(1);
    } else {
        reservationSuccessRate.add(0);
        if (res.status === 400 || res.status === 409) {
            lockFailedCounter.add(1);
        }
    }

    sleep(0.1);
}
