// 시나리오 B: 서로 다른 좌석 병렬 선점 (전역 락이 아님을 증명)
//
// 목적: 유저 N명이 각자 서로 다른 좌석을 동시에 요청했을 때, 좌석 단위 락이
//       전역 락처럼 직렬화되지 않고 병렬로 처리되는지 확인한다.
//       성공 건수가 요청 유저 수와 동일해야 하고(경합 자체가 없으므로),
//       동일 VU 수·환경에서 시나리오 A 대비 처리량(TPS)이 유의미하게 높아야 한다.
//
// "좌석 병렬성" — 전역 락으로 직렬화되면 설계 실패로 간주)
//
// 데이터 소스: 커밋 1의 01-prepare-performance.ps1이 만든 users.json을 그대로 읽는다.
//   각 유저는 준비 스크립트에서 이미 좌석 1개씩 1:1로 배정받았다(assignedSeatId).
import http from 'k6/http';
import {check} from 'k6';
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

const GAME_ID = __ENV.GAME_ID; // manifest.testGameId

// 이 실행이 어느 락 전략으로 기동된 서버를 대상으로 하는지 라벨링한다.
// 서버 설정을 바꾸지 않는다 — 실제 전략 전환은 앱 재기동(04-run-strategy-matrix.ps1)이 담당한다.
const LOCK_STRATEGY = __ENV.LOCK_STRATEGY;
const ALLOWED_STRATEGIES = ['distributed', 'pessimistic', 'optimistic'];
if (!ALLOWED_STRATEGIES.includes(LOCK_STRATEGY)) {
    // 오타 라벨로 측정 결과 전체를 오독하는 사고를 기동 즉시 차단한다.
    throw new Error(`LOCK_STRATEGY 값이 올바르지 않습니다: '${LOCK_STRATEGY}'. 허용값: ${ALLOWED_STRATEGIES.join(', ')}`);
}

export const options = {
    scenarios: {
        different_seat_parallel: {
            executor: 'per-vu-iterations',
            vus: users.length,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        hold_unexpected_error: ['count==0'],
        hold_user_not_verified: ['count==0'],
        hold_max_seat_count_exceeded: ['count==0'],
        // 시나리오 A와 다른 핵심 기대치: 좌석 경합이 없으므로 실패(409)가 0에 가까워야 한다.
        // hold_extension_limit_exceeded가 0이 아니면 대부분 "재선점 10분 상한"에 걸린 것이니
        // 결함으로 보기 전에 회차 간 간격부터 의심한다(위 설계 관점 참고).
        hold_seat_already_held: ['count==0'],
        hold_extension_limit_exceeded: ['count==0'],
        // 시나리오 A와 대비되는 시나리오 B의 성공 기준: 전원 성공해야 한다.
        hold_success: [`count==${users.length}`],
    },
};

export default function () {
    const user = users[(__VU - 1) % users.length];

    const res = http.post(
        `${__ENV.BASE_URL}/api/v1/reservations`,
        JSON.stringify({
            gameId: Number(GAME_ID),
            gameSeatIds: [Number(user.assignedSeatId)], // 유저마다 서로 다른 좌석 — 경합 없음
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Queue-Token': user.admissionToken,
                'Authorization': `Bearer ${user.accessToken}`,
            },
        }
    );

    seatHoldDuration.add(res.timings.duration, {lock_strategy: LOCK_STRATEGY});

    // res.json()은 응답이 JSON이 아니면 예외를 던져 iteration 자체가 중단된다.
    // 이러면 가장 심각한 장애가 hold_unexpected_error에 잡히지 않고 조용히 사라지므로 try/catch로 감싼다.
    let errorCode = null;
    if (res.status !== 201) {
        try {
            errorCode = res.json('errorCode');
        } catch (e) {
            errorCode = null; // JSON 파싱 실패 — 아래 분기에서 holdUnexpectedError로 집계됨
        }
    }

    if (res.status === 201) {
        holdSuccess.add(1);
    } else if (res.status === 409 && errorCode === 'SEAT_ALREADY_HELD') {
        // 시나리오 B에서는 정상적으로는 나오면 안 된다(좌석이 유저마다 다름).
        // 나오면 준비 스크립트가 assignedSeatId를 중복 배정했을 가능성을 의심한다.
        holdSeatAlreadyHeld.add(1);
    } else if (res.status === 409 && errorCode === 'LOCK_FAILED') {
        holdLockFailed.add(1);
    } else if (res.status === 403 && errorCode === 'USER_NOT_VERIFIED') {
        holdUserNotVerified.add(1);
    } else if (res.status === 400 && errorCode === 'MAX_SEAT_COUNT_EXCEEDED') {
        holdMaxSeatCountExceeded.add(1);
    } else if (res.status === 409 && errorCode === 'HOLD_EXTENSION_LIMIT_EXCEEDED') {
        // 파일 상단 경고 참고 — 회차 간격이 10분을 넘겼을 가능성이 가장 크다.
        holdExtensionLimitExceeded.add(1);
    } else {
        holdUnexpectedError.add(1);
    }

    check(res, {
        '선점 성공(201)': (r) => r.status === 201,
    });
}