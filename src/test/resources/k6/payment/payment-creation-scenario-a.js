import http from 'k6/http';
import {check} from 'k6';
import {Counter, Rate} from 'k6/metrics';
import {SharedArray} from 'k6/data';
import exec from 'k6/execution';

// 결제 생성 성능 테스트 - 시나리오 A: 서로 다른 주문의 신규 결제 생성
//
// 측정 목적
// - 서로 다른 사용자가 각자의 CREATED 주문으로 결제를 처음 생성할 때의 응답시간과 처리량을 측정한다.
// - 결제 생성 과정의 주문별 Redis 분산락과 DB 조회·저장 비용을 함께 관찰한다.
//
// 실행 전 준비
// 1. Docker Compose와 백엔드를 실행하고 01-setup-demo-data.ps1로 기준 데이터를 준비한다.
// 2. prepare-payment-creation-data.ps1를 실행해 사용자·주문·Access Token JSON을 생성한다.
// 3. 이 시나리오는 주문마다 결제를 새로 만들기 때문에 매 실행 전에 준비 스크립트를 다시 실행해야 한다.
//
// 실행 예시
// k6 run -e STAGE_USERS=100 src/test/resources/k6/payment/payment-creation-scenario-a.js

const paymentCreationSuccessRate = new Rate('payment_creation_success_rate');
const createdCounter = new Counter('payment_creation_created_count');
const clientErrorCounter = new Counter('payment_creation_client_error_count');
const unexpectedErrorCounter = new Counter('payment_creation_unexpected_error_count');
const serverErrorRate = new Rate('payment_creation_server_error_rate');

// 준비 스크립트가 만든 사용자별 인증 정보와 신규·재요청용 결제 데이터를 모든 VU가 공유한다.
// 서버 환경에서는 USER_DATA_PATH로 별도로 준비한 JSON 경로를 전달할 수 있다.
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
const users = new SharedArray('payment creation users', () =>
    JSON.parse(open(USER_DATA_PATH))
);

const stageUserCount = Number(__ENV.STAGE_USERS || '100');

if (!Number.isInteger(stageUserCount) || stageUserCount <= 0) {
    throw new Error('STAGE_USERS는 0보다 큰 정수여야 합니다.');
}

if (users.length < stageUserCount) {
    throw new Error(`준비된 사용자 수가 부족합니다. required=${stageUserCount}, actual=${users.length}`);
}

const loadStage = `${stageUserCount}_users`;
const thresholds = {
    'http_req_duration{name:payment_creation_new}': ['p(99)<2000'], // 신규 생성 요청의 p99가 2초 미만
    payment_creation_server_error_rate: ['rate<0.01'],             // 5xx 응답 비율이 1% 미만
    payment_creation_success_rate: ['rate>0.99'],                  // 200·READY 응답 비율이 99% 초과
    dropped_iterations: ['count==0'],                              // 예정한 모든 사용자 요청이 실행됨
};

// 실행 결과에서 사용자 수별 측정치를 바로 구분할 수 있도록 load_stage 태그에도 같은 기준을 적용한다.
thresholds[`http_req_duration{name:payment_creation_new,load_stage:${loadStage}}`] = ['p(99)<2000'];
thresholds[`payment_creation_server_error_rate{load_stage:${loadStage}}`] = ['rate<0.01'];
thresholds[`payment_creation_success_rate{load_stage:${loadStage}}`] = ['rate>0.99'];

export const options = {
    scenarios: {
        create_new_payments: {
            // 사용자마다 한 번만 요청해 하나의 주문에 결제가 두 번 생성되는 상황을 방지한다.
            executor: 'per-vu-iterations',
            vus: stageUserCount,
            iterations: 1,
            maxDuration: '60s',
            tags: {
                load_stage: loadStage,
            },
        },
    },
    thresholds,
};

function getCurrentUser() {
    // 각 VU가 한 번만 실행되므로 전체 iteration 순번을 사용자 JSON 인덱스로 사용할 수 있다.
    const userIndex = exec.scenario.iterationInTest;
    const user = users[userIndex];

    if (!user) {
        throw new Error(`사용자 데이터를 찾을 수 없습니다. userIndex=${userIndex}`);
    }

    return user;
}

export default function () {
    const user = getCurrentUser();

    // 사용자마다 서로 다른 주문과 멱등키를 보내 주문별 락 경합이 아닌 신규 생성 처리량을 측정한다.
    const response = http.post(
        `${BASE_URL}/api/v1/payments`,
        JSON.stringify({orderId: user.newOrderId}),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${user.accessToken}`,
                'Idempotency-Key': user.newIdempotencyKey,
            },
            // k6가 200 응답을 예상 응답으로 분류하게 하되, 아래에서 응답 본문까지 별도로 검증한다.
            responseCallback: http.expectedStatuses(200),
            tags: {
                name: 'payment_creation_new',
            },
        }
    );

    // 상태 코드가 200이어도 다른 주문이나 READY가 아닌 결제가 반환되면 성공으로 집계하지 않는다.
    let responseData = null;
    try {
        responseData = JSON.parse(response.body).data;
    } catch (_) {
    }

    const created = response.status === 200
        && responseData?.orderId === user.newOrderId
        && responseData?.status === 'READY';

    // HTTP 오류와 비즈니스 응답 불일치를 분리해 병목과 데이터 준비 오류를 구분한다.
    paymentCreationSuccessRate.add(created ? 1 : 0);
    serverErrorRate.add(response.status >= 500 ? 1 : 0);

    if (created) {
        createdCounter.add(1);
    } else if (response.status >= 400 && response.status < 500) {
        clientErrorCounter.add(1);
    } else if (response.status < 500) {
        unexpectedErrorCounter.add(1);
    }

    check(response, {
        'new payment is created': () => created,
    });
}
