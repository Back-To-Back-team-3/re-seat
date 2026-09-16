// 시나리오 C: 인기 좌석(INFIELD) 소수 경합 + 일반 좌석 분산 처리 혼합 부하
//
// 목적: 실제 티켓팅에서 관측되는 "명당 자리(내야석)는 몰리고 나머지는 원활"이라는
//       현실적인 패턴을 재현해, 인기 좌석 경합이 일반 좌석 처리 지연에 얼마나
//       전파되는지(락 대기가 DB 커넥션 풀을 오래 잡아 일반 좌석까지 느려지는지) 확인한다.
//
// 좌석 풀 정의: 임의 비율이 아니라 실제 SeatGrade.INFIELD로 정의한다
//   (PricePolicy 기준 INFIELD가 항상 OUTFIELD보다 비쌈 — "명당 자리"의 실제 근거).
//   POPULAR_SEAT_IDS는 01-prepare-performance.ps1이 유저 50명에게 배정한 앞쪽 50석
//   (gameSeatIds 오름차순 LIMIT 50)과 절대 겹치지 않도록 사전 쿼리로 뽑는다.
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

const GAME_ID = __ENV.GAME_ID;

// 사전 쿼리로 확보한 INFIELD 20석 (배정된 50석과 겹치지 않음). 쉼표 구분 문자열로 전달.
const POPULAR_SEAT_IDS = __ENV.POPULAR_SEAT_IDS.split(',').map(Number);

// VU의 20%를 인기 풀 경합으로 보낸다. 고정 매핑(VU 번호 기반)이라 실행마다 동일한 유저가
// 동일한 그룹에 속해, 우연한 그룹 배정 차이로 결과가 흔들리는 것을 방지한다(시나리오 B와 동일 원칙).
const POPULAR_GROUP_RATIO = 0.2;

export const options = {
    scenarios: {
        mixed_load: {
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
        // 일반 풀은 경합이 없으므로 시나리오 B와 동일하게 이 값이 0이어야 정상.
        // 0이 아니면 회차 간격이 10분을 넘겼을 가능성을 먼저 의심한다.
        hold_extension_limit_exceeded: ['count==0'],
    },
};

function pickSeat(vuIndex, userAssignedSeatId) {
    const isPopularGroup = (vuIndex % 10) < (POPULAR_GROUP_RATIO * 10);
    if (isPopularGroup) {
        const seatId = POPULAR_SEAT_IDS[Math.floor(Math.random() * POPULAR_SEAT_IDS.length)];
        return {seatId, pool: 'popular'};
    }
    return {seatId: userAssignedSeatId, pool: 'general'};
}

export default function () {
    const vuIndex = __VU - 1;
    const user = users[vuIndex % users.length];
    const {seatId, pool} = pickSeat(vuIndex, user.assignedSeatId);

    const res = http.post(
        `${__ENV.BASE_URL}/api/v1/reservations`,
        JSON.stringify({gameId: Number(GAME_ID), gameSeatIds: [Number(seatId)]}),
        {
            headers: {
                'Content-Type': 'application/json',
                'Queue-Token': user.admissionToken,
                'Authorization': `Bearer ${user.accessToken}`,
            },
        }
    );

    // 풀별로 태깅해서 Grafana에서 인기/일반 지연시간을 분리 관찰한다.
    seatHoldDuration.add(res.timings.duration, {seat_pool: pool});

    const errorCode = res.status !== 201 ? res.json('errorCode') : null;

    if (res.status === 201) {
        holdSuccess.add(1, {seat_pool: pool});
    } else if (res.status === 409 && errorCode === 'SEAT_ALREADY_HELD') {
        // 인기 풀에서는 정상적으로 발생 가능(경합). 일반 풀에서 나오면 배정 겹침을 의심한다.
        holdSeatAlreadyHeld.add(1, {seat_pool: pool});
    } else if (res.status === 409 && errorCode === 'LOCK_FAILED') {
        holdLockFailed.add(1, {seat_pool: pool});
    } else if (res.status === 403 && errorCode === 'USER_NOT_VERIFIED') {
        holdUserNotVerified.add(1);
    } else if (res.status === 400 && errorCode === 'MAX_SEAT_COUNT_EXCEEDED') {
        holdMaxSeatCountExceeded.add(1);
    } else if (res.status === 409 && errorCode === 'HOLD_EXTENSION_LIMIT_EXCEEDED') {
        holdExtensionLimitExceeded.add(1);
    } else {
        holdUnexpectedError.add(1);
    }

    check(res, {
        'HTTP 응답 수신': (r) => r.status !== 0,
    });
}