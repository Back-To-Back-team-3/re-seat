import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// C(선점) 파트 부하 테스트 - 시나리오 B: 서로 다른 좌석 병렬 처리
// 측정 목적: N명이 각각 다른 gameSeatId로 동시 선점 요청 시 글로벌 락 직렬화 없이 병렬 처리되는지 확인

const reservationSuccessRate = new Rate('reservation_success_rate');
const seatAlreadyHeldCounter = new Counter('seat_already_held_count');
const lockFailedCounter = new Counter('lock_failed_count');
const unexpectedCounter = new Counter('unexpected_error_count');

export const options = {
    scenarios: {
        parallel_different_seats: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '10s', target: 50 },  // 10초간 VU 50까지 증가
                { duration: '10s', target: 100 }, // 10초간 VU 100까지 증가
                { duration: '20s', target: 100 }, // 20초간 VU 100 상태 유지 (지속 부하)
                { duration: '10s', target: 0 },   // 10초간 감소
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(99)<2000'],       // p99 지연 2초 이하
        http_req_failed: ['rate<0.01'],          // 5xx 서버 에러율 1% 미만
        // reservation_success_rate threshold 미적용:
        // 좌석 소진 후 SEAT_ALREADY_HELD(409)는 정상 응답이므로 TPS 비교로 판단
    },
};

// 환경 변수 설정
const BASE_URL     = __ENV.BASE_URL     || 'http://localhost:8080';
const JWT_TOKEN    = __ENV.JWT_TOKEN    || 'DUMMY_JWT_TOKEN';
const QUEUE_TOKENS = __ENV.QUEUE_TOKENS ? __ENV.QUEUE_TOKENS.split(',') : ['DUMMY_QUEUE_TOKEN'];
const GAME_ID      = __ENV.GAME_ID      ? parseInt(__ENV.GAME_ID)     : 1;
const MIN_SEAT_ID  = __ENV.MIN_SEAT_ID  ? parseInt(__ENV.MIN_SEAT_ID) : 1;
const MAX_SEAT_ID  = __ENV.MAX_SEAT_ID  ? parseInt(__ENV.MAX_SEAT_ID) : 103;
const SEAT_COUNT   = MAX_SEAT_ID - MIN_SEAT_ID + 1;

export default function () {
    // VU별 유니크 gameSeatId — 실제 ID 범위 안에서 순환 할당
    const gameSeatId = ((__VU - 1) % SEAT_COUNT) + MIN_SEAT_ID;
    const queueToken = QUEUE_TOKENS[(__VU - 1) % QUEUE_TOKENS.length];

    const res = http.post(
        `${BASE_URL}/api/v1/reservations`,
        JSON.stringify({ gameId: GAME_ID, gameSeatIds: [gameSeatId] }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${JWT_TOKEN}`,
                'Queue-Token': queueToken,
            },
            responseCallback: http.expectedStatuses(201, 409), // 201·409는 예상 응답
        }
    );

    // 응답 에러코드 파싱
    let errorCode = '';
    try { errorCode = JSON.parse(res.body).errorCode || ''; } catch (_) {}

    // 에러코드별 카운터 집계
    if (res.status === 201) {
        reservationSuccessRate.add(1);
    } else if (errorCode === 'SEAT_ALREADY_HELD') {
        reservationSuccessRate.add(0);
        seatAlreadyHeldCounter.add(1);
    } else if (errorCode === 'LOCK_FAILED') {
        reservationSuccessRate.add(0);
        lockFailedCounter.add(1);
    } else {
        reservationSuccessRate.add(0);
        unexpectedCounter.add(1);
    }

    // 응답 상태 검증
    check(res, {
        'status is 201': (r) => r.status === 201,
        'status is 409 SEAT_ALREADY_HELD': (r) => errorCode === 'SEAT_ALREADY_HELD',
        'status is 409 LOCK_FAILED': (r) => errorCode === 'LOCK_FAILED',
    });
}
