import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

// JWT 발급 및 서명 검증 처리량·지연시간 부하 테스트

// 토큰 발급 메트릭
const jwtIssueSuccessRate = new Rate('jwt_issue_success_rate');
const jwtIssueServerErrorRate = new Rate('jwt_issue_server_error_rate');
const jwtIssueSuccessCount = new Counter('jwt_issue_success_count');
const jwtIssueClientErrorCount = new Counter('jwt_issue_client_error_count');

// 토큰 검증 메트릭
const jwtVerifySuccessRate = new Rate('jwt_verify_success_rate');
const jwtVerifyServerErrorRate = new Rate('jwt_verify_server_error_rate');
const jwtVerifySuccessCount = new Counter('jwt_verify_success_count');
const jwtVerifyClientErrorCount = new Counter('jwt_verify_client_error_count');

// 환경 변수 설정
const USER_DATA_PATH = __ENV.USER_DATA_PATH || './build/k6/queue-entry-users.json';
const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const TEST_PASSWORD = __ENV.TEST_PASSWORD || 'Test123!';

const isHttpsBaseUrl = /^https:\/\/[^/\s]+(?:\/.*)?$/i.test(BASE_URL);
const isAllowedHttpBaseUrl = /^http:\/\/(?:localhost|127\.0\.0\.1|\[::1\]|web-app)(?::\d+)?(?:\/.*)?$/i.test(BASE_URL);

if (!isHttpsBaseUrl && !isAllowedHttpBaseUrl) {
    throw new Error(
        `BASE_URL은 HTTPS를 사용해야 하며, HTTP는 로컬 또는 Docker 내부 주소에서만 사용할 수 있습니다. url=${BASE_URL}`,
    );
}

// 사용자 토큰 데이터 검증 및 로드 (잘못된 더미 폴백 제거)
const users = new SharedArray('jwt test users', () => {
    let data;
    try {
        data = JSON.parse(open(USER_DATA_PATH));
    } catch (e) {
        throw new Error(`사용자 토큰 데이터 파일(${USER_DATA_PATH})을 열거나 파싱할 수 없습니다: ${e.message}`);
    }

    if (!Array.isArray(data) || data.length === 0) {
        throw new Error(`사용자 토큰 데이터가 비어 있거나 배열 형식이 아닙니다. path=${USER_DATA_PATH}`);
    }

    for (let i = 0; i < data.length; i++) {
        const item = data[i];
        if (!item || typeof item.accessToken !== 'string' || item.accessToken.trim() === '') {
            throw new Error(`유효하지 않거나 비어 있는 accessToken이 포함되어 있습니다. index=${i}`);
        }
    }

    return data;
});

const VUS = parseInt(__ENV.VUS || '50', 10);
const DURATION = __ENV.DURATION || '30s';
const loadStage = `${VUS}_vus`;

const thresholds = {
    'http_req_duration{scenario:jwt_issue_scenario}': ['p(99)<1000'],
    'http_req_duration{scenario:jwt_verify_scenario}': ['p(99)<500'],
    jwt_issue_server_error_rate: ['rate<0.01'],
    jwt_issue_success_rate: ['rate>0.99'],
    jwt_verify_server_error_rate: ['rate<0.01'],
    jwt_verify_success_rate: ['rate>0.99'],
};

export const options = {
    scenarios: {
        // 시나리오 1: JWT 토큰 발급 부하 (로그인 API 호출)
        jwt_issue_scenario: {
            executor: 'constant-vus',
            exec: 'issueTokens',
            vus: VUS,
            duration: DURATION,
            tags: {
                scenario: 'jwt_issue_scenario',
                load_stage: loadStage,
            },
        },
        // 시나리오 2: JWT 서명 검증 부하 (인가 필터 + 내 정보 조회)
        jwt_verify_scenario: {
            executor: 'constant-vus',
            exec: 'verifyTokens',
            vus: VUS,
            duration: DURATION,
            tags: {
                scenario: 'jwt_verify_scenario',
                load_stage: loadStage,
            },
        },
    },
    thresholds,
};

// JWT 토큰 발급 시나리오 실행 함수
export function issueTokens() {
    const userIndex = exec.scenario.iterationInTest % users.length;
    const user = users[userIndex];
    const email = user.email || `queue-load-${String(userIndex + 1).padStart(4, '0')}@example.com`;
    const password = user.password || TEST_PASSWORD;

    const payload = JSON.stringify({ email, password });

    const res = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        payload,
        {
            headers: {
                'Content-Type': 'application/json',
            },
            tags: {
                name: 'jwt_token_issue',
            },
        }
    );

    const isSuccess = res.status === 200;
    jwtIssueSuccessRate.add(isSuccess ? 1 : 0);
    jwtIssueServerErrorRate.add(res.status >= 500 ? 1 : 0);

    if (isSuccess) {
        jwtIssueSuccessCount.add(1);
    } else if (res.status >= 400 && res.status < 500) {
        jwtIssueClientErrorCount.add(1);
    }

    check(res, {
        'issue status is 200 OK': (r) => r.status === 200,
        'response contains accessToken': (r) => {
            try {
                const body = JSON.parse(r.body);
                return !!(body && body.data && body.data.accessToken);
            } catch (e) {
                return false;
            }
        },
    });
}

// JWT 서명 검증 시나리오 실행 함수
export function verifyTokens() {
    const userIndex = exec.scenario.iterationInTest % users.length;
    const user = users[userIndex];

    const res = http.get(
        `${BASE_URL}/api/v1/users/me`,
        {
            headers: {
                Authorization: `Bearer ${user.accessToken}`,
                'Content-Type': 'application/json',
            },
            tags: {
                name: 'jwt_token_verify',
            },
        }
    );

    const isSuccess = res.status === 200;
    jwtVerifySuccessRate.add(isSuccess ? 1 : 0);
    jwtVerifyServerErrorRate.add(res.status >= 500 ? 1 : 0);

    if (isSuccess) {
        jwtVerifySuccessCount.add(1);
    } else if (res.status >= 400 && res.status < 500) {
        jwtVerifyClientErrorCount.add(1);
    }

    check(res, {
        'verify status is 200 OK': (r) => r.status === 200,
    });
}

export default function () {
    verifyTokens();
}
