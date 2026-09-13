import http from 'k6/http';
import {check} from 'k6';
import {Counter, Rate} from 'k6/metrics';
import {SharedArray} from 'k6/data';

// 결제 생성 성능 테스트 - 시나리오 B: 동일 멱등키 재요청
//
// 측정 목적
// - 서로 다른 사용자가 각자의 READY 결제에 동일한 주문·멱등키로 재요청할 때의 성능을 측정한다.
// - 신규 INSERT 없이 기존 결제를 조회·검증해 반환하는 멱등 처리 경로의 비용을 확인한다.
//
// 실행 전 준비
// 1. Docker Compose와 백엔드를 실행하고 01-setup-demo-data.ps1로 기준 데이터를 준비한다.
// 2. prepare-payment-creation-data.ps1를 실행해 사용자별 READY 결제와 Access Token JSON을 생성한다.
// 3. 각 VU는 자신의 결제만 반복 요청하므로 서로 다른 주문 사이의 락 경합은 발생하지 않는다.
//
// 실행 예시
// k6 run -e STAGE_USERS=100 -e ITERATIONS_PER_VU=10 \
//   src/test/resources/k6/payment/payment-creation-scenario-b.js

const paymentReplaySuccessRate = new Rate('payment_creation_replay_success_rate');
const replayCounter = new Counter('payment_creation_replay_count');
const responseMismatchCounter = new Counter('payment_creation_replay_mismatch_count');
const clientErrorCounter = new Counter('payment_creation_replay_client_error_count');
const serverErrorRate = new Rate('payment_creation_replay_server_error_rate');

// 두 시나리오가 동일한 사용자 집합을 사용하되 서로 다른 주문을 사용해 결과가 섞이지 않게 한다.
const USER_DATA_PATH = __ENV.USER_DATA_PATH || '../../../../../build/k6/payment-creation-users.json';
const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

// 외부 서버에는 HTTPS만 허용하고, HTTP는 개발 환경 주소에서만 허용한다.
const isHttpsBaseUrl = /^https:\/\/[^/\s]+(?:\/.*)?$/i.test(BASE_URL);
const isAllowedHttpBaseUrl = /^http:\/\/(?:localhost|127\.0\.0\.1|\[::1\]|web-app)(?::\d+)?(?:\/.*)?$/i.test(BASE_URL);

if (!isHttpsBaseUrl && !isAllowedHttpBaseUrl) {
    throw new Error(
        `BASE_URL은 HTTPS를 사용해야 하며, HTTP는 로컬 또는 Docker 내부 주소에서만 사용할 수 있습니다. url=${BASE_URL}`,
    );
}

// SharedArray를 사용해 VU마다 같은 JSON 파일을 중복 적재하지 않는다.
const users = new SharedArray('payment creation replay users', () =>
    JSON.parse(open(USER_DATA_PATH))
);

const stageUserCount = Number(__ENV.STAGE_USERS || '100');
const iterationsPerVu = Number(__ENV.ITERATIONS_PER_VU || '10');

if (!Number.isInteger(stageUserCount) || stageUserCount <= 0) {
    throw new Error('STAGE_USERS는 0보다 큰 정수여야 합니다.');
}

if (!Number.isInteger(iterationsPerVu) || iterationsPerVu <= 0) {
    throw new Error('ITERATIONS_PER_VU는 0보다 큰 정수여야 합니다.');
}

if (users.length < stageUserCount) {
    throw new Error(`준비된 사용자 수가 부족합니다. required=${stageUserCount}, actual=${users.length}`);
}

const loadStage = `${stageUserCount}_users`;
const thresholds = {
    'http_req_duration{name:payment_creation_replay}': ['p(99)<2000'], // 멱등 재요청의 p99가 2초 미만
    payment_creation_replay_server_error_rate: ['rate<0.01'],          // 5xx 응답 비율이 1% 미만
    payment_creation_replay_success_rate: ['rate>0.99'],               // 기존 결제 일치 응답이 99% 초과
    dropped_iterations: ['count==0'],                                  // 예정한 모든 반복 요청이 실행됨
};

// 실행 결과에서 사용자 수별 측정치를 바로 구분할 수 있도록 load_stage 태그에도 같은 기준을 적용한다.
thresholds[`http_req_duration{name:payment_creation_replay,load_stage:${loadStage}}`] = ['p(99)<2000'];
thresholds[`payment_creation_replay_server_error_rate{load_stage:${loadStage}}`] = ['rate<0.01'];
thresholds[`payment_creation_replay_success_rate{load_stage:${loadStage}}`] = ['rate>0.99'];

export const options = {
    scenarios: {
        replay_existing_payments: {
            // 한 VU가 자신의 결제를 반복 호출해 사용자 간 데이터 공유 없이 멱등 경로에 지속 부하를 준다.
            executor: 'per-vu-iterations',
            vus: stageUserCount,
            iterations: iterationsPerVu,
            maxDuration: '60s',
            tags: {
                load_stage: loadStage,
            },
        },
    },
    thresholds,
};

function getCurrentUser() {
    // 한 VU가 여러 iteration을 실행하므로 iteration 순번이 아닌 고정된 VU 번호로 사용자를 배정한다.
    const userIndex = __VU - 1;
    const user = users[userIndex];

    if (!user) {
        throw new Error(`사용자 데이터를 찾을 수 없습니다. userIndex=${userIndex}`);
    }

    return user;
}

export default function () {
    const user = getCurrentUser();

    // 준비된 주문·멱등키를 그대로 반복해 매 요청이 기존 READY 결제를 반환하는 경로를 타게 한다.
    const response = http.post(
        `${BASE_URL}/api/v1/payments`,
        JSON.stringify({orderId: user.replayOrderId}),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${user.accessToken}`,
                'Idempotency-Key': user.replayIdempotencyKey,
            },
            // k6가 200 응답을 예상 응답으로 분류하게 하되, 아래에서 기존 결제 ID까지 별도로 검증한다.
            responseCallback: http.expectedStatuses(200),
            tags: {
                name: 'payment_creation_replay',
            },
        }
    );

    // 같은 주문이어도 다른 결제가 반환되면 멱등 처리 실패이므로 성공으로 집계하지 않는다.
    let responseData = null;
    try {
        responseData = JSON.parse(response.body).data;
    } catch (_) {
    }

    const replayed = response.status === 200
        && responseData?.paymentId === user.replayPaymentId
        && responseData?.orderId === user.replayOrderId
        && responseData?.status === 'READY';

    // HTTP 오류와 200 응답의 결제 정보 불일치를 별도 메트릭으로 분리한다.
    paymentReplaySuccessRate.add(replayed ? 1 : 0);
    serverErrorRate.add(response.status >= 500 ? 1 : 0);

    if (replayed) {
        replayCounter.add(1);
    } else if (response.status >= 400 && response.status < 500) {
        clientErrorCounter.add(1);
    } else if (response.status < 500) {
        responseMismatchCounter.add(1);
    }

    check(response, {
        'existing payment is returned': () => replayed,
    });
}
