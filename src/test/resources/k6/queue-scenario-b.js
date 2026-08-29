import http from 'k6/http';
import {check, sleep} from 'k6';
import {Counter, Rate} from 'k6/metrics';
import {SharedArray} from 'k6/data';
import exec from 'k6/execution';

// B(대기열) 파트 부하 테스트 — 시나리오 B: 대기 상태 반복 조회
// 측정 목적: 서로 다른 N명이 대기열 진입 후 상태를 반복 조회할 때 응답시간과 성공 · 서버 오류율 측정

// Queue 상태 조회 결과용 메트릭 정의
const queueStatusSuccessRate = new Rate('queue_status_success_rate');
const clientErrorCounter = new Counter('queue_status_client_error_count');
const unexpectedErrorCounter = new Counter('queue_status_unexpected_error_count');
const serverErrorRate = new Rate('queue_status_server_error_rate');

// 환경 변수 설정
// 서로 다른 사용자가 대기열 진입 후 상태를 반복 조회할 때 요청 처리 성능과 오류율을 측정한다.
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

// 전체 사용자를 여러 묶음으로 준비하므로 setup 실행 제한 시간을 별도로 설정한다.
const SETUP_TIMEOUT = __ENV.SETUP_TIMEOUT || '10m';

if (!Number.isInteger(GAME_ID) || GAME_ID <= 0) {
    throw new Error('GAME_ID는 0보다 큰 정수여야 합니다.');
}

const pollCount = parseInt(__ENV.POLL_COUNT || '10', 10);
const pollIntervalSeconds = parseFloat(__ENV.POLL_INTERVAL_SECONDS || '1');

if (!Number.isInteger(pollCount) || pollCount <= 0) {
    throw new Error('POLL_COUNT는 0보다 큰 정수여야 합니다.');
}

if (!Number.isFinite(pollIntervalSeconds) || pollIntervalSeconds <= 0) {
    throw new Error('POLL_INTERVAL_SECONDS는 0보다 큰 숫자여야 합니다.');
}

// Queue 진입 병목이 상태 조회 측정에 포함되지 않도록 준비 단계의 동시 요청수를 제한한다.
const prepareBatchSize = parseInt(__ENV.PREPARE_BATCH_SIZE || '10', 10);
const preparePollIntervalSeconds = parseFloat(__ENV.PREPARE_POLL_INTERVAL_SECONDS || '1');
const prepareTimeoutSeconds= parseFloat(__ENV.PREPARE_TIMEOUT_SECONDS || '30');

if (!Number.isInteger(prepareBatchSize) || prepareBatchSize <= 0) {
    throw new Error('PREPARE_BATCH_SIZE는 0보다 큰 정수여야 합니다.');
}

if (!Number.isFinite(preparePollIntervalSeconds) || preparePollIntervalSeconds <= 0) {
    throw new Error('PREPARE_POLL_INTERVAL_SECONDS는 0보다 큰 숫자여야 합니다.');
}

if (!Number.isFinite(prepareTimeoutSeconds) || prepareTimeoutSeconds <= 0) {
    throw new Error('PREPARE_TIMEOUT_SECONDS는 0보다 큰 숫자여야 합니다.');
}

// 사용자별 Access Token 데이터를 한 번만 읽어 모든 VU가 공유한다.
const users = new SharedArray('queue entry users', () =>
    JSON.parse(open(USER_DATA_PATH))
);

const stageUserCount = Number(__ENV.STAGE_USERS || '500');

if (!Number.isInteger(stageUserCount) || stageUserCount <= 0) {
    throw new Error('STAGE_USERS는 0보다 큰 정수여야 합니다.');
}

const requiredUserCount = stageUserCount;

if (users.length < requiredUserCount) {
    throw new Error(`준비된 사용자 수가 부족합니다. required=${requiredUserCount}, actual=${users.length}`);
}

const thresholds = {
    'http_req_duration{name:queue_status}': ['p(99)<2000'], // p99 지연 2초 이하
    queue_status_server_error_rate: ['rate<0.01'],          // 5xx 서버 에러율 1% 미만
    queue_status_success_rate: ['rate>0.99'],               // 200 응답 성공률 99% 초과
};

// 현재 부하 단계의 상태 조회 응답시간과 성공 · 서버 오류율을 집계한다.
const loadStage = `${stageUserCount}_users`;

thresholds[`http_req_duration{name:queue_status,load_stage:${loadStage}}`] = ['p(99)<2000'];
thresholds[`queue_status_server_error_rate{load_stage:${loadStage}}`] = ['rate<0.01'];
thresholds[`queue_status_success_rate{load_stage:${loadStage}}`] = ['rate>0.99'];

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
    setupTimeout: SETUP_TIMEOUT,
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

// 상태 조회 부하 측정 전에 사용자를 낮은 동시성으로 Queue에 진입시킨다.
// 준비 요청은 별도 태그로 분리해 상태 조회 측정값에서 제외한다.
export function setup() {
    const prepareUsers = users.slice(0, requiredUserCount);

    // 각 묶음의 준비 완료를 확인한 후 다음 묶음으로 넘어가 Queue 진입 부하를 제한한다.
    for (let batchStart = 0; batchStart < prepareUsers.length; batchStart += prepareBatchSize) {
        const batchUsers = prepareUsers.slice(batchStart, batchStart + prepareBatchSize);

        const requests = batchUsers.map((user) => ({
            method: 'POST',
            url: `${BASE_URL}/api/v1/queues/${GAME_ID}/enter`,
            body: null,
            params: {
                headers: {
                    Authorization: `Bearer ${user.accessToken}`,
                },
                responseCallback: http.expectedStatuses(202),
                tags: {
                    name: 'queue_prepare_entry',
                },
            },
        }));

        const responses = http.batch(requests);
        responses.forEach((response, batchIndex) => {
            if (response.status !== 202) {
                const userIndex = batchStart + batchIndex;

                throw new Error(
                    `Queue 진입 준비에 실패했습니다. userIndex=${userIndex}, status=${response.status}`
                );
            }
        });

        // 제한 시간 초과 시 실패한 사용자와 마지막 응답 상태를 확인할 수 있도록 준비 상태를 저장한다.
        let pendingUsers = batchUsers.map((user, batchIndex) => ({
            user,
            userIndex: batchStart + batchIndex,
            lastStatus: 0,
        }));

        const prepareDeadline = Date.now() + prepareTimeoutSeconds * 1_000;

        // Queue Consumer 등록 전의 404는 준비 중 상태로 처리하고, 상태 조회가 200이 될 때까지 기다린다.
        while(pendingUsers.length > 0) {
            const statusRequests = pendingUsers.map((pendingUser) => ({
                method: 'GET',
                url: `${BASE_URL}/api/v1/queues/${GAME_ID}/me`,
                params: {
                    headers: {
                        Authorization: `Bearer ${pendingUser.user.accessToken}`,
                    },
                    responseCallback: http.expectedStatuses(200, 404),
                    tags: {
                        name: 'queue_prepare_status',
                    },
                },
            }));

            const statusResponses = http.batch(statusRequests);

            pendingUsers = pendingUsers
                .map((pendingUser, index) => ({
                    ...pendingUser,
                    lastStatus: statusResponses[index].status,
                }))
                .filter((pendingUser) => pendingUser.lastStatus !== 200);

            if (pendingUsers.length > 0) {
                // 준비 단계가 무한 대기하지 않도록 제한 시간 안에서만 확인한다.
                if (Date.now() >= prepareDeadline) {
                    const pendingUserStatuses = pendingUsers
                        .map((pendingUser) => `userIndex=${pendingUser.userIndex}, status=${pendingUser.lastStatus}`)
                        .join(', ');

                    throw new Error(
                        'Queue 준비가 제한 시간을 초과했습니다. ' + pendingUserStatuses
                    );
                }

                sleep(preparePollIntervalSeconds);
            }
        }
    }
}

export default function () {
    const user = getCurrentUser();

    // 실제 상태 조회 주기에 맞춰 각 사용자가 일정 간격으로 정해진 횟수만큼 조회한다.
    for (let pollIndex = 0; pollIndex < pollCount; pollIndex++) {
        sleep(pollIntervalSeconds);

        const statusResponse = http.get(
            `${BASE_URL}/api/v1/queues/${GAME_ID}/me`,
            {
                headers: {
                    Authorization: `Bearer ${user.accessToken}`,
                },
                responseCallback: http.expectedStatuses(200),
                tags: {
                    name: 'queue_status',
                },
            }
        );

        // 카운터 집계
        queueStatusSuccessRate.add(statusResponse.status === 200 ? 1 : 0);
        serverErrorRate.add(statusResponse.status >= 500 ? 1 : 0);

        if (statusResponse.status >= 400 && statusResponse.status < 500) {
            clientErrorCounter.add(1);
        } else if (statusResponse.status !== 200 && statusResponse.status < 500) {
            unexpectedErrorCounter.add(1);
        }

        // 응답 상태 검증
        check(statusResponse, {
            'queue status is returned': (result) => result.status === 200,
        });
    }
}
