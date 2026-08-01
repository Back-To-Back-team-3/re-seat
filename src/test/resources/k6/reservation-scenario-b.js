import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// C(선점) 파트 부하 테스트 - 시나리오 B: 서로 다른 좌석 병렬 처리
// 측정 목적: N명이 각각 다른 gameSeatId로 동시 선점 요청 시 글로벌 락 직렬화 없이 병렬 처리되는지 확인
// 기대: 시나리오 A 대비 TPS가 유의미하게 높아야 함 (API 명세서 5.1 동시성 규칙 검증)


const reservationSuccessRate = new Rate('reservation_success_rate');

export const options = {
    scenarios: {
        parallel_different_seats: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '10s', target: 50 },  // 10초간 VU 50까지 증가
                { duration: '20s', target: 100 }, // 20초간 VU 100 유지
                { duration: '10s', target: 0 },   // 10초간 감소
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(99)<2000'], // p99 지연 2초 이하
        http_req_failed: ['rate<0.01'],    // 5xx 서버 에러율 1% 미만
    },
};

// 환경 변수 설정
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_TOKEN = __ENV.JWT_TOKEN || 'DUMMY_JWT_TOKEN';
const QUEUE_TOKEN = __ENV.QUEUE_TOKEN || 'DUMMY_QUEUE_TOKEN';
const GAME_ID = __ENV.GAME_ID ? parseInt(__ENV.GAME_ID) : 1;
const START_SEAT_ID = __ENV.START_SEAT_ID ? parseInt(__ENV.START_SEAT_ID) : 1000;

export default function () {
    // VU 식별자(__VU)와 반복 회차(__ITER)를 활용하여 각 요청마다 서로 다른 고유 gameSeatId 생성
    const uniqueSeatId = START_SEAT_ID + (__VU * 100) + __ITER;

    const url = `${BASE_URL}/api/v1/reservations`;

    const payload = JSON.stringify({
        gameId: GAME_ID,
        gameSeatIds: [uniqueSeatId], // 각 VU가 서로 다른 좌석 선점 시도
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${JWT_TOKEN}`,
            'Queue-Token': QUEUE_TOKEN,
        },
    };

    const res = http.post(url, payload, params);

    const isSuccess = check(res, {
        'status is 201 (Created)': (r) => r.status === 201,
    });

    reservationSuccessRate.add(isSuccess ? 1 : 0);

    sleep(0.05);
}
