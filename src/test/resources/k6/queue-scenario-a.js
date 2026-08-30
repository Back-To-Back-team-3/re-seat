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

// https://로 시작하고 공백 없는 호스트가 이어지며 포트와 경로를 허용한다.
const isHttpsBaseUrl = /^https:\/\/[^/\s]+(?:\/.*)?$/i.test(BASE_URL);

// http:// 뒤에 로컬 주소 또는 web-app이 오며 선택적으로 포트와 경로를 허용한다.
const isAllowedHttpBaseUrl = /^http:\/\/(?:localhost|127\.0\.0\.1|\[::1\]|web-app)(?::\d+)?(?:\/.*)?$/i.test(BASE_URL);

if (!isHttpsBaseUrl && !isAllowedHttpBaseUrl) {
    throw new Error(
        `BASE_URL은 HTTPS를 사용해야 하며, HTTP는 로컬 또는 Docker 내부 주소에서만 사용할 수 있습니다. url=${BASE_URL}`,
    );
}

const GAME_ID = parseInt(__ENV.GAME_ID || '117', 10);

if (!Number.isInteger(GAME_ID) || GAME_ID <= 0) {
    throw new Error('GAME_ID는 0보다 큰 정수여야 합니다.');
}

// 사용자별 Access Token 데이터를 한 번만 읽어 모든 VU가 공유한다.
const users = new SharedArray('queue entry users', () =>
    JSON.parse(open(USER_DATA_PATH))
);

const stageUserCount = Number(__ENV.STAGE_USERS || '300');

if (!Number.isInteger(stageUserCount) || stageUserCount <= 0) {
    throw new Error('STAGE_USERS는 0보다 큰 정수여야 합니다.');
}

const requiredUserCount = stageUserCount;

if (users.length < requiredUserCount) {
    throw new Error(`준비된 사용자 수가 부족합니다. required=${requiredUserCount}, actual=${users.length}`);
}

const thresholds = {
    http_req_duration: ['p(99)<2000'],            // p99 지연 2초 이하
    queue_entry_server_error_rate: ['rate<0.01'], // 5xx 서버 에러율 1% 미만
    queue_entry_success_rate: ['rate>0.99'],      // 202 응답 성공률 99% 초과
    dropped_iterations: ['count==0'],             // 모든 사용자의 대기열 진입 실행 완료
};

// 현재 부하 단계의 응답시간과 성공 · 서버 오류율을 집계한다.
const loadStage = `${stageUserCount}_users`;

thresholds[`http_req_duration{load_stage:${loadStage}}`] = ['p(99)<2000'];
thresholds[`queue_entry_server_error_rate{load_stage:${loadStage}}`] = ['rate<0.01'];
thresholds[`queue_entry_success_rate{load_stage:${loadStage}}`] = ['rate>0.99'];

export const options = {
    scenarios: {
        stage_1: {
            executor: 'per-vu-iterations',
            vus: stageUserCount,
            iterations: 1,
            maxDuration: '60s',
            tags: {
                load_stage: `${stageUserCount}_users`,
            },
        },
    },
    thresholds,
};

// 각 VU에 서로 다른 사용자를 배정한다.
function getCurrentUser() {
    const userIndex = exec.scenario.iterationInTest;
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
