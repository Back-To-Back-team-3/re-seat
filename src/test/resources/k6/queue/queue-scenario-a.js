import http from 'k6/http';
import {check, sleep} from 'k6';
import {Counter, Rate, Trend} from 'k6/metrics';
import {SharedArray} from 'k6/data';
import exec from 'k6/execution';

// B(대기열) 파트 부하 테스트 — 시나리오 A: 대기열 동시 진입
// 측정 목적: 서로 다른 N명이 같은 경기의 대기열에 동시에 진입할 때 요청 접수와 실제 등록 완료 성능을 구분해 측정

// Queue 진입 접수와 등록 완료 결과용 메트릭 정의
const queueEntrySuccessRate = new Rate('queue_entry_success_rate');
const acceptedCounter = new Counter('queue_entry_accepted_count');
const clientErrorCounter = new Counter('queue_entry_client_error_count');
const unexpectedErrorCounter = new Counter('queue_entry_unexpected_error_count');
const serverErrorRate = new Rate('queue_entry_server_error_rate');
const networkErrorCounter = new Counter('queue_entry_network_error_count');
const registrationCompletedRate = new Rate('queue_registration_completed_rate');
const registrationDuration = new Trend('queue_registration_duration', true);
const registrationCompletedCounter = new Counter('queue_registration_completed_count');
const registrationTimeoutCounter = new Counter('queue_registration_timeout_count');
const registrationErrorCounter = new Counter('queue_registration_error_count');
const queueEntryExpectedStatuses = http.expectedStatuses(202);
const queueStatusExpectedStatuses = http.expectedStatuses(200, 404);

// 환경 변수 설정
// 서로 다른 사용자가 같은 경기의 대기열에 동시에 진입할 때 요청 접수와 실제 등록 완료 성능을 구분해 측정한다.
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

const registrationTimeoutSeconds = Number(__ENV.REGISTRATION_TIMEOUT_SECONDS || '30');

if (!Number.isFinite(registrationTimeoutSeconds) || registrationTimeoutSeconds <= 0) {
    throw new Error('REGISTRATION_TIMEOUT_SECONDS는 0보다 큰 숫자여야 합니다.');
}

const pollIntervalSeconds = Number(__ENV.POLL_INTERVAL_SECONDS || '1');

if (!Number.isFinite(pollIntervalSeconds) || pollIntervalSeconds <= 0) {
    throw new Error('POLL_INTERVAL_SECONDS는 0보다 큰 숫자여야 합니다.');
}

const requestTimeoutSeconds = Number(__ENV.REQUEST_TIMEOUT_SECONDS || '2');

if (!Number.isFinite(requestTimeoutSeconds) || requestTimeoutSeconds <= 0) {
    throw new Error('REQUEST_TIMEOUT_SECONDS는 0보다 큰 숫자여야 합니다.');
}

if (requestTimeoutSeconds > registrationTimeoutSeconds) {
    throw new Error('REQUEST_TIMEOUT_SECONDS는 REGISTRATION_TIMEOUT_SECONDS보다 클 수 없습니다.');
}

// 진입 요청 제한시간, 등록 확인 제한시간과 종료 여유를 포함해 실행 제한 시간을 계산한다.
const scenarioShutdownBufferSeconds = 10;
const scenarioMaxDurationSeconds = Math.ceil(
    requestTimeoutSeconds + registrationTimeoutSeconds + scenarioShutdownBufferSeconds
);

const thresholds = {
    'http_req_duration{name:queue_entry}': ['p(99)<2000'],  // 대기열 진입 접수 p99 2초 이하
    queue_entry_server_error_rate: ['rate<0.01'],           // 대기열 진입 5xx 서버 오류율 1% 미만
    queue_entry_success_rate: ['rate>0.99'],                // 202 응답 성공률 99% 초과
    queue_registration_completed_rate: ['rate>0.99'],       // 202 접수 후 등록 완료율 99% 초과
    queue_registration_duration: ['p(99)<30000'],           // 등록 완료 p99 30초 미만
    dropped_iterations: ['count==0'],                       // 모든 사용자의 대기열 진입과 등록 확인 완료
};

// 현재 부하 단계의 접수 응답시간, 성공 · 서버 오류율과 등록 완료 결과를 집계한다.
const loadStage = `${stageUserCount}_users`;

thresholds[`http_req_duration{name:queue_entry,load_stage:${loadStage}}`] = ['p(99)<2000'];
thresholds[`queue_entry_server_error_rate{load_stage:${loadStage}}`] = ['rate<0.01'];
thresholds[`queue_entry_success_rate{load_stage:${loadStage}}`] = ['rate>0.99'];
thresholds[`queue_registration_completed_rate{load_stage:${loadStage}}`] = ['rate>0.99'];
thresholds[`queue_registration_duration{load_stage:${loadStage}}`] = ['p(99)<30000'];

export const options = {
    scenarios: {
        stage_1: {
            executor: 'per-vu-iterations',
            vus: stageUserCount,
            iterations: 1,
            maxDuration: `${scenarioMaxDurationSeconds}s`,
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
    const requestStartedAt = Date.now();

    // 대기열 진입 요청
    // 이번 실행에서 생성한 사용자와 경기로 요청한다.
    const entryResponse = http.post(
        `${BASE_URL}/api/v1/queues/${GAME_ID}/enter`,
        null,
        {
            headers: {
                Authorization: `Bearer ${user.accessToken}`,
            },
            responseCallback: queueEntryExpectedStatuses,
            tags: {
                name: 'queue_entry',
            },
            timeout: `${requestTimeoutSeconds}s`,
        }
    );

    // 진입 요청 결과 집계
    queueEntrySuccessRate.add(entryResponse.status === 202 ? 1 : 0);
    serverErrorRate.add(entryResponse.status >= 500 ? 1 : 0);

    if (entryResponse.status === 202) {
        acceptedCounter.add(1);
    } else if (entryResponse.status === 0) {
        networkErrorCounter.add(1);
    } else if (entryResponse.status >= 400 && entryResponse.status < 500) {
        clientErrorCounter.add(1);
    } else if (entryResponse.status < 500){
        unexpectedErrorCounter.add(1);
    }

    // 응답 상태 검증
    check(entryResponse, {
        'queue entry is accepted': (result) => result.status === 202,
    });

    if (entryResponse.status !== 202) {
        return;
    }

    const acceptedAt = Date.now();
    const registrationDeadline = acceptedAt + registrationTimeoutSeconds * 1_000;

    let registrationCompleted = false;
    let registrationOutcome = 'pending';
    let registrationErrorReason = null;

    // Consumer 등록 전의 QUEUE_ENTRY_NOT_FOUND는 제한 시간 안에서 다시 확인한다.
    while (Date.now() < registrationDeadline) {
        const remainingSeconds = Math.max(0, registrationDeadline - Date.now()) / 1_000;
        const isDeadlineBound = remainingSeconds <= requestTimeoutSeconds;
        const pollRequestTimeoutSeconds = Math.min(requestTimeoutSeconds, remainingSeconds);

        const statusResponse = http.get(
            `${BASE_URL}/api/v1/queues/${GAME_ID}/me`,
            {
                headers: {
                    Authorization: `Bearer ${user.accessToken}`,
                },
                responseCallback: queueStatusExpectedStatuses,
                tags: {
                    name: 'queue_registration_poll',
                },
                timeout: `${pollRequestTimeoutSeconds}s`,
            }
        );

        if (statusResponse.status !== 200 && statusResponse.status !== 404) {
            if (isDeadlineBound
                && statusResponse.status === 0
                && statusResponse.error_code === 1050
            ) {
                registrationOutcome = 'timeout';
                break;
            }

            registrationOutcome = 'error';

            if (statusResponse.status === 0) {
                registrationErrorReason = 'network_error';
            } else if (statusResponse.status >= 400 && statusResponse.status < 500) {
                registrationErrorReason = 'client_error';
            } else if (statusResponse.status >= 500) {
                registrationErrorReason = 'server_error';
            } else {
                registrationErrorReason = 'unexpected_status';
            }

            break;
        }

        let responseBody = null;

        try {
            responseBody = statusResponse.json();
        } catch (e) {
            registrationOutcome = 'error';
            registrationErrorReason = 'response_parse_error';
            break;
        }

        if (statusResponse.status === 200) {
            const data = responseBody !== null ? responseBody.data : null;

            const isWaiting =
                responseBody !== null
                && responseBody !== undefined
                && responseBody.success === true
                && data !== null
                && data !== undefined
                && data.queueStatus === 'WAITING'
                && data.rank >= 1
                && data.admitted === false;

            const isAdmitted =
                responseBody !== null
                && responseBody !== undefined
                && responseBody.success === true
                && data !== null
                && data !== undefined
                && data.queueStatus === 'ADMITTED'
                && data.rank === 0
                && data.admitted === true;

            if (isWaiting || isAdmitted) {
                registrationCompleted = true;
                registrationOutcome = 'completed';
                break;
            }

            registrationOutcome = 'error';
            registrationErrorReason = 'invalid_success_body';
            break;
        }

        const isNotRegisteredYet =
            responseBody !== null
            && responseBody !== undefined
            && responseBody.success === false
            && responseBody.errorCode === 'QUEUE_ENTRY_NOT_FOUND';

        if (!isNotRegisteredYet) {
            registrationOutcome = 'error';
            registrationErrorReason = 'client_error';
            break;
        }

        const currentRemainingSeconds = (registrationDeadline - Date.now()) / 1_000;
        const sleepSeconds = Math.min(pollIntervalSeconds, currentRemainingSeconds);

        if (sleepSeconds > 0) {
            sleep(sleepSeconds);
        }
    }

    if (registrationOutcome === 'pending') {
        registrationOutcome = 'timeout';
    }

    // 등록 결과 집계
    // 등록 완료 시간에는 POST 요청부터 정상 WAITING 또는 ADMITTED 상태를 확인할 때까지의 시간을 기록한다.
    if (registrationOutcome === 'completed') {
        registrationDuration.add(Date.now() - requestStartedAt);
        registrationCompletedCounter.add(1);
    } else if (registrationOutcome === 'timeout') {
        registrationTimeoutCounter.add(1);
    } else if (registrationOutcome === 'error') {
        registrationErrorCounter.add(1, { reason: registrationErrorReason });
    }

    registrationCompletedRate.add(registrationCompleted ? 1 : 0);

    // 등록 완료 상태 검증
    check(null, {
        'queue registration is completed': () => registrationCompleted === true,
    });
}
