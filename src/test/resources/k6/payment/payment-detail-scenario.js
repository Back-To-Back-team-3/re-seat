import http from 'k6/http';
import {check} from 'k6';
import {Counter, Rate} from 'k6/metrics';
import {SharedArray} from 'k6/data';

// 결제 상세 조회 성능 테스트 - 여러 결제 ID 분산 조회
//
// 측정 목적
// - 서로 다른 사용자가 각자의 READY 결제를 반복 조회할 때의 응답시간과 처리량을 측정한다.
// - 데이터 변경이나 락 경합 없이 결제 단건 조회와 소유권 검증에 드는 비용을 확인한다.
//
// 실행 전 준비
// 1. Docker Compose와 백엔드를 실행하고 01-setup-demo-data.ps1로 기준 데이터를 준비한다.
// 2. prepare-payment-creation-data.ps1를 실행해 사용자별 READY 결제와 Access Token JSON을 생성한다.
// 3. 조회만 수행하므로 한 번 준비한 데이터로 반복 실행할 수 있다.
//
// 실행 예시
// k6 run -e STAGE_USERS=100 -e ITERATIONS_PER_VU=10 -e MAX_DURATION=60s \
//   src/test/resources/k6/payment/payment-detail-scenario.js

const paymentDetailSuccessRate = new Rate('payment_detail_success_rate');
const detailCounter = new Counter('payment_detail_success_count');
const responseMismatchCounter = new Counter('payment_detail_response_mismatch_count');
const clientErrorCounter = new Counter('payment_detail_client_error_count');
const serverErrorRate = new Rate('payment_detail_server_error_rate');

// 결제 생성 성능 테스트의 READY 결제 픽스처를 조회 대상으로 재사용한다.
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
const users = new SharedArray('payment detail users', () =>
    JSON.parse(open(USER_DATA_PATH))
);

const stageUserCount = Number(__ENV.STAGE_USERS || '100');
const iterationsPerVu = Number(__ENV.ITERATIONS_PER_VU || '10');
const maxDuration = __ENV.MAX_DURATION || '60s';

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
    'http_req_duration{name:payment_detail}': ['p(99)<2000'], // 결제 상세 조회의 p99가 2초 미만
    payment_detail_server_error_rate: ['rate<0.01'],          // 5xx 응답 비율이 1% 미만
    payment_detail_success_rate: ['rate>0.99'],               // 본인 결제 일치 응답이 99% 초과
    dropped_iterations: ['count==0'],                         // 예정한 모든 반복 요청이 실행됨
};

// 실행 결과에서 사용자 수별 측정치를 바로 구분할 수 있도록 load_stage 태그에도 같은 기준을 적용한다.
thresholds[`http_req_duration{name:payment_detail,load_stage:${loadStage}}`] = ['p(99)<2000'];
thresholds[`payment_detail_server_error_rate{load_stage:${loadStage}}`] = ['rate<0.01'];
thresholds[`payment_detail_success_rate{load_stage:${loadStage}}`] = ['rate>0.99'];

export const options = {
    scenarios: {
        read_payment_details: {
            // 한 VU가 자신의 결제만 반복 조회해 사용자 간 데이터 공유 없이 읽기 부하를 준다.
            executor: 'per-vu-iterations',
            vus: stageUserCount,
            iterations: iterationsPerVu,
            maxDuration,
            tags: {
                load_stage: loadStage,
            },
        },
    },
    thresholds,
};

function getCurrentUser() {
    // 한 VU가 여러 iteration을 실행하므로 고정된 VU 번호로 조회할 사용자와 결제를 배정한다.
    const userIndex = __VU - 1;
    const user = users[userIndex];

    if (!user) {
        throw new Error(`사용자 데이터를 찾을 수 없습니다. userIndex=${userIndex}`);
    }

    return user;
}

export default function () {
    const user = getCurrentUser();

    const response = http.get(
        `${BASE_URL}/api/v1/payments/${user.replayPaymentId}`,
        {
            headers: {
                Authorization: `Bearer ${user.accessToken}`,
            },
            // k6가 200 응답을 예상 응답으로 분류하게 하되, 아래에서 결제 정보까지 별도로 검증한다.
            responseCallback: http.expectedStatuses(200),
            tags: {
                name: 'payment_detail',
            },
        }
    );

    let responseData = null;
    try {
        responseData = JSON.parse(response.body).data;
    } catch (_) {
    }

    // 상태 코드가 200이어도 다른 사용자 데이터나 예상과 다른 상태가 반환되면 성공으로 집계하지 않는다.
    const matched = response.status === 200
        && responseData?.paymentId === user.replayPaymentId
        && responseData?.orderId === user.replayOrderId
        && responseData?.status === 'READY';

    paymentDetailSuccessRate.add(matched ? 1 : 0);
    serverErrorRate.add(response.status >= 500 ? 1 : 0);

    if (matched) {
        detailCounter.add(1);
    } else if (response.status >= 400 && response.status < 500) {
        clientErrorCounter.add(1);
    } else if (response.status < 500) {
        responseMismatchCounter.add(1);
    }

    check(response, {
        'owned payment detail is returned': () => matched,
    });
}
