// 시나리오 A: 동일 좌석 경합 (over-booking 방지 검증)
//
// 목적: 서로 다른 유저 N명이 정확히 동일한 gameSeatId 하나를 동시에 노렸을 때
//       성공(201)이 정확히 1건만 발생하고, 나머지는 전부 정책적 실패(409)로
//       처리되는지 정량적으로 확인한다. 성공이 2건 이상이면 over-booking이며,
//       hold_unexpected_error가 1건이라도 있으면 원인 불명 결함이다.
//
// 데이터 소스: 테스트 전용 Queue-Token 발급 API는 레포에 존재하지 않으므로,
//   01-prepare-performance.ps1이 만든 build/k6/runs/<RunId>/users.json을 그대로 읽는다(SharedArray). 
//
// executor 선택 이유: constant-vus + duration으로 VU가 계속 루프를 돌면,
//   승자(HELD 성공)는 곧바로 자기 좌석과 충돌하고 패자들도 계속 재시도하게 되어
//   "동시 요청 1회씩"이라는 레이스 테스트의 전제 자체가 깨진다.
//   → 유저 1명당 정확히 1회만, 동시에 발사하는 per-vu-iterations로 구성한다.
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
// 시나리오 A는 "전원이 같은 좌석 하나를 노리는" 시나리오이므로 유저별 assignedSeatId를
// 쓰지 않고, manifest.gameSeatIds 중 하나를 대상 좌석으로 명시적으로 지정받는다.
const TARGET_SEAT_ID = __ENV.TARGET_SEAT_ID;

export const options = {
    scenarios: {
        same_seat_race: {
            executor: 'per-vu-iterations',
            // VU 수 = users.json에 준비된 유저 수. 유저 1명 = VU 1개 = 요청 1회.
            vus: users.length,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        // 진짜 결함(5xx 등 원인 불명)은 반드시 0건이어야 over-booking 무결성이 증명된다.
        hold_unexpected_error: ['count==0'],
        // 아래 3개는 픽스처가 정상이라면 절대 나오면 안 되는 값들이다.
        // 하나라도 0이 아니면 "동시성 결함"이 아니라 "준비 스크립트 문제"이므로
        // 즉시 커밋 1 스크립트(is_verified, 좌석 잔존, admission_token 재사용)를 먼저 확인한다.
        hold_user_not_verified: ['count==0'],
        hold_max_seat_count_exceeded: ['count==0'],
        hold_extension_limit_exceeded: ['count==0'],
    },
};

export default function () {
    // __VU는 1부터 시작하므로 0-base 인덱스로 변환해 users.json과 1:1 매핑한다.
    const user = users[(__VU - 1) % users.length];

    const res = http.post(
        `${__ENV.BASE_URL}/api/v1/reservations`,
        JSON.stringify({
            gameId: Number(GAME_ID),
            gameSeatIds: [Number(TARGET_SEAT_ID)], // 전원 동일 좌석 지정 — 경합 유발
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Queue-Token': user.admissionToken,
                'Authorization': `Bearer ${user.accessToken}`,
            },
        }
    );

    // 종단(end-to-end) 응답 시간 — Queue-Token 검증부터 DB 트랜잭션 커밋까지 전체 구간.
    seatHoldDuration.add(res.timings.duration);

    // 실패 응답의 errorCode는 GlobalExceptionHandler → ApiResponse.failure(code, message)로
    // 내려오는 값이며, 성공(201) 응답에는 이 필드가 없으므로 실패일 때만 파싱한다.
    const errorCode = res.status !== 201 ? res.json('errorCode') : null;

    if (res.status === 201) {
        holdSuccess.add(1);
    } else if (res.status === 409 && errorCode === 'SEAT_ALREADY_HELD') {
        // ReservationService.validateSeatsForGame()에서 던지는 정상 경합 결과 — 실패로 취급하지 않는다.
        holdSeatAlreadyHeld.add(1);
    } else if (res.status === 409 && errorCode === 'LOCK_FAILED') {
        // RedissonSeatLockStrategy / RedissonUserGameLockStrategy의 tryLock 대기(3초/5초) 초과.
        // 경합이 심하다는 신호일 뿐 결함은 아니다.
        holdLockFailed.add(1);
    } else if (res.status === 403 && errorCode === 'USER_NOT_VERIFIED') {
        // SeatHoldFacade 1단계에서 걸린다 — 나오면 준비 스크립트의 is_verified 시딩을 의심한다.
        holdUserNotVerified.add(1);
    } else if (res.status === 400 && errorCode === 'MAX_SEAT_COUNT_EXCEEDED') {
        // SeatHoldFacade 3-1단계(SeatCountPolicy)에서 걸린다 — 나오면 해당 유저가 이전 회차의
        // 예약을 그대로 보유 중이라는 뜻이므로 02-reset-performance.ps1 실행 여부를 의심한다.
        holdMaxSeatCountExceeded.add(1);
    } else if (res.status === 409 && errorCode === 'HOLD_EXTENSION_LIMIT_EXCEEDED') {
        // SeatHoldFacade 2-1단계(HoldExtensionPolicy)에서 걸린다 — 시나리오 A는 유저당 1회성
        // 요청이므로 나오면 admission_token이 이전 회차 것과 겹쳤다는 뜻이다.
        holdExtensionLimitExceeded.add(1);
    } else {
        // 위 어느 것에도 안 걸리는 나머지(5xx 등) — 여기가 0건이어야 진짜 over-booking 무결성 증명이 된다.
        holdUnexpectedError.add(1);
    }

    check(res, {
        // 실제 "성공이 정확히 1건"인지는 VU 단위로는 판단 불가능하므로 진짜 검증은
        // 전체 회차 종료 후 03-cleanup-performance.ps1 실행 전에 별도로 DB(reservation_seats,
        // game_seats.status)를 조회해서 확인한다. 이 check는 응답을 받았다는 것만 표시한다.
        'HTTP 응답 수신': (r) => r.status !== 0,
    });
}