import http from 'k6/http';
import {check} from 'k6';
import {Counter, Rate} from 'k6/metrics';
import {SharedArray} from 'k6/data';
import exec from 'k6/execution';

// B(대기열) 파트 부하 테스트 — 시나리오 A: 대기열 동시 진입
// 측정 목적: 서로 다른 N명이 같은 경기의 대기열에 동시에 진입할 때 응답시간과 성공 · 서버 오류율 측정

// Queue 진입 결과용 메트릭 정의
const queueEntrySuccessRate = new Rate('queue_entry_success_rate');
const acceptedCounter = new Counter('queue_entry_accepted_count');
const clientErrorCounter = new Counter('queue_entry_client_error_count');
const unexpectedErrorCounter = new Counter('queue_entry_unexpected_error_count');
const serverErrorRate = new Rate('queue_entry_server_error_rate');

// 환경 변수 설정
// 서로 다른 사용자가 같은 경기의 대기열에 동시에 진입할 때 요청 처리 성능과 오류율을 측정한다.
const USER_DATA_PATH = __ENV.USER_DATA_PATH || '../../../../build/k6/queue-entry-users.json';
const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const GAME_ID = parseInt(__ENV.GAME_ID || '117', 10);

if (!Number.isInteger(GAME_ID) || GAME_ID <= 0) {
    throw new Error('GAME_ID는 0보다 큰 정수여야 합니다.');
}

// 사용자별 Access Token 데이터를 한 번만 읽어 모든 VU가 공유한다.
const users = new SharedArray('queue entry users', () =>
    JSON.parse(open(USER_DATA_PATH))
);

const stageUserCounts = (__ENV.STAGE_USERS || '250,275,300')
    .split(',')
    .map((value) => parseInt(value.trim(), 10));

if (stageUserCounts.some((count) => !Number.isInteger(count) || count <= 0)) {
    throw new Error('STAGE_USERS는 0보다 큰 정수 목록이여야 합니다.');
}

const requiredUserCount = stageUserCounts.reduce((sum, count) => sum + count, 0);

if (users.length < requiredUserCount) {
    throw new Error(`준비된 사용자 수가 부족합니다. required=${requiredUserCount}, actual=${users.length}`);
}

const stageIntervalSeconds = parseInt(__ENV.STAGE_INTERVAL_SECONDS || '15', 10);

if (!Number.isInteger(stageIntervalSeconds) || stageIntervalSeconds <= 0) {
    throw new Error('STAGE_INTERVAL_SECONDS는 0보다 큰 정수여야 합니다.');
}

const scenarios = {};

stageUserCounts.forEach((userCount, index) => {
   const stageNumber = index + 1;

   scenarios[`stage_${stageNumber}`] = {
       executor: 'per-vu-iterations',
       vus: userCount,
       iterations: 1,
       startTime: `${index * stageIntervalSeconds}s`,
       maxDuration: '60s',
       tags: {
           load_stage: `${userCount}_users`,
       },
   };
});

const thresholds = {
    http_req_duration: ['p(99)<2000'],            // p99 지연 2초 이하
    queue_entry_server_error_rate: ['rate<0.01'], // 5xx 서버 에러율 1% 미만
    queue_entry_success_rate: ['rate>0.99'],      // 202 응답 성공률 99% 초과
};

// 각 부하 단계의 응답시간과 성공 · 서버 오류율을 별도로 집계한다.
stageUserCounts.forEach((userCount) => {
   const loadStage = `${userCount}_users`;

   thresholds[`http_req_duration{load_stage:${loadStage}}`] = ['p(99)<2000'];
   thresholds[`queue_entry_server_error_rate{load_stage:${loadStage}}`] = ['rate<0.01'];
   thresholds[`queue_entry_success_rate{load_stage:${loadStage}}`] = ['rate>0.99'];
});

export const options = {
    scenarios,
    thresholds,
};

// 이전 단계의 사용자 수만큼 건너뛰어 단계마다 서로 다른 사용자를 배정한다.
function getCurrentUser() {
    const stageIndex = parseInt(exec.scenario.name.replace('stage_', ''), 10) - 1;

    const userOffset = stageUserCounts
        .slice(0, stageIndex)
        .reduce((sum, count) => sum + count, 0);

    const userIndex = userOffset + exec.scenario.iterationInTest;
    const user = users[userIndex];

    if (!user) {
        throw new Error(`사용자 데이터를 찾을 수 없습니다. userIndex=${userIndex}`);
    }

    return user;
}

export default function () {
    const user = getCurrentUser();

    const res = http.post(
        `${BASE_URL}/api/v1/queues/${GAME_ID}/enter`,
        null,
        {
            headers: {
                Authorization: `Bearer ${user.accessToken}`,
            },
            responseCallback: http.expectedStatuses(202),
            tags: {
                name: 'queue_entry',
            },
        }
    );

    // 카운터 집계
    queueEntrySuccessRate.add(res.status === 202 ? 1 : 0);
    serverErrorRate.add(res.status >= 500 ? 1 : 0);

    if (res.status === 202) {
        acceptedCounter.add(1);
    } else if (res.status >= 400 && res.status < 500) {
        clientErrorCounter.add(1);
    } else if (res.status < 500){
        unexpectedErrorCounter.add(1);
    }

    // 응답 상태 검증
    check(res, {
        'queue entry is accepted': (result) => result.status === 202,
    });
}
