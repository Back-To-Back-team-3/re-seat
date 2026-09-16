// 시나리오 D: 예매 오픈 순간 급격한 유입 재현 (스파이크 부하)
//
// 목적: constant-vus(A/B/C에서 사용)는 이미 워밍업된 VU 상태를 가정하지만, 실제 티켓팅 오픈은 "0초에 목표 요청률로 즉시 도달"하는 상황이다.
//       도착률(arrival rate) 기반 executor로 이를 재현하고, HikariCP·Redisson 커넥션 풀이 어느 지점에서 고갈되어 5xx가 급증하는지 관찰한다.
//
// HikariCP 풀 크기 확정 근거: /actuator/prometheus 실측 결과 hikaricp_connections_max=10.0, hikaricp_connections_min=10.0 (고정 풀 10)
//
// 관찰 대상 지표(k6 결과와 별도로 /actuator/prometheus에서 함께 확인):
//   hikaricp_connections_active  — 사용 중 커넥션 수 (최대 10)
//   hikaricp_connections_pending — 커넥션을 못 받고 대기 중인 스레드 수 (이 값이 0을 벗어나는 시점이 병목 시작점)
//   hikaricp_connections_timeout_total — 커넥션 획득 타임아웃 총 횟수
//
// 이 시나리오는 over-booking 검증이 아니라 순수 부하 관찰이 목적이므로, 유저를 랜덤 순환 재사용한다(동일 유저·좌석 중복 요청이 발생해도 무방).
// 데이터 소스: 커밋 1의 01-prepare-performance.ps1이 만든 users.json.
import http from 'k6/http';
import {SharedArray} from 'k6/data';
import {
    seatHoldDuration,
    holdSuccess,
    holdSeatAlreadyHeld,
    holdLockFailed,
    holdUnexpectedError,
    holdUserNotVerified,
    holdMaxSeatCountExceeded,
    holdExtensionLimitExceeded,
} from './reservation-metrics.js';

// users.json 스키마: { userId, email, accessToken, admissionToken, assignedSeatId }[]
const users = new SharedArray('reservation-users', function () {
    return JSON.parse(open(__ENV.USERS_FILE));
});

const GAME_ID = __ENV.GAME_ID;

// 500석 전체 풀에서 매 반복마다 새 좌석을 뽑는다(assignedSeatId 50개만 재사용하면
// 최초 50건 이후 전부 "가벼운 거절"로 끝나 HikariCP를 전혀 압박하지 못한다 — 1차 실측 확인).
const allSeatIds = __ENV.ALL_SEAT_IDS.split(',').map(Number);

export const options = {
    scenarios: {
        booking_open_spike: {
            executor: 'ramping-arrival-rate',
            startRate: 20,
            timeUnit: '1s',
            preAllocatedVUs: 100,
            maxVUs: 1000,
            stages: [
                {target: 100, duration: '10s'},  // 여유 구간 — 기준선
                {target: 300, duration: '10s'},  // 풀 처리 한계(~450) 근접
                {target: 600, duration: '10s'},  // 한계 초과 — pending/5xx 관찰 목표 구간
                {target: 0, duration: '10s'},    // 감소, 정상 복귀 확인
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.7'], // 스파이크 구간
    },
};

export default function () {
    // 유저 수(50)보다 요청 수가 더 많아질 수 있으므로 랜덤 순환 사용한다.
    const user = users[Math.floor(Math.random() * users.length)];
    // 500석 전체에서 매 반복마다 다른 좌석을 뽑아 실제 "새로운 HOLD 쓰기"가
    // 계속 발생하게 한다(위 1차 실측 원인 참고).
    const seatId = allSeatIds[Math.floor(Math.random() * allSeatIds.length)];

    const res = http.post(
        `${__ENV.BASE_URL}/api/v1/reservations`,
        JSON.stringify({gameId: Number(GAME_ID), gameSeatIds: [seatId]}),
        {
            headers: {
                'Content-Type': 'application/json',
                'Queue-Token': user.admissionToken,
                'Authorization': `Bearer ${user.accessToken}`,
            },
        }
    );

    seatHoldDuration.add(res.timings.duration);

    const errorCode = res.status !== 201 ? res.json('errorCode') : null;

    if (res.status === 201) {
        holdSuccess.add(1);
    } else if (res.status === 409 && errorCode === 'SEAT_ALREADY_HELD') {
        // 랜덤 순환 재사용으로 동일 좌석에 여러 요청이 겹치면 정상적으로 발생 가능.
        holdSeatAlreadyHeld.add(1);
    } else if (res.status === 409 && errorCode === 'LOCK_FAILED') {
        holdLockFailed.add(1);
    } else if (res.status === 403 && errorCode === 'USER_NOT_VERIFIED') {
        holdUserNotVerified.add(1);
    } else if (res.status === 400 && errorCode === 'MAX_SEAT_COUNT_EXCEEDED') {
        holdMaxSeatCountExceeded.add(1);
    } else if (res.status === 409 && errorCode === 'HOLD_EXTENSION_LIMIT_EXCEEDED') {
        holdExtensionLimitExceeded.add(1);
    } else {
        // HikariCP 풀 고갈로 인한 타임아웃, 5xx 등 — 이 시나리오의 핵심 관찰 대상.
        holdUnexpectedError.add(1);
    }
}