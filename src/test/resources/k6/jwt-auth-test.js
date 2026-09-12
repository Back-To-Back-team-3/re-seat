import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

// JWT 인증 처리 성능 및 필터 지연시간 측정 부하 테스트
const jwtAuthSuccessRate = new Rate('jwt_auth_success_rate');
const jwtAuthServerErrorRate = new Rate('jwt_auth_server_error_rate');
const successCounter = new Counter('jwt_auth_success_count');
const clientErrorCounter = new Counter('jwt_auth_client_error_count');

// 테스트용 유저 토큰 데이터 파일 경로
const USER_DATA_PATH = __ENV.USER_DATA_PATH || './build/k6/queue-entry-users.json';
const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

// 미리 생성된 유저별 Access Token 로드
const users = new SharedArray('jwt test users', () => {
    try {
        return JSON.parse(open(USER_DATA_PATH));
    } catch (e) {
        // 단일 테스트 토큰 fallback 예시
        return [{ accessToken: 'dummy-token-for-test' }];
    }
});

const VUS = parseInt(__ENV.VUS || '50', 10);
const DURATION = __ENV.DURATION || '30s';
const loadStage = `${VUS}_vus`;

const thresholds = {
    http_req_duration: ['p(99)<1000'],           // JWT 검증 포함 p99 1초 이하
    jwt_auth_server_error_rate: ['rate<0.01'],   // 5xx 서버 에러율 1% 미만
    jwt_auth_success_rate: ['rate>0.99'],        // 200 OK 응답 성공률 99% 초과
};

export const options = {
    scenarios: {
        jwt_load_test: {
            executor: 'constant-vus',
            vus: VUS,
            duration: DURATION,
            tags: {
                load_stage: loadStage,
            },
        },
    },
    thresholds,
};

export default function () {
    const userIndex = exec.scenario.iterationInTest % users.length;
    const user = users[userIndex];

    // 인증이 필요한 마이페이지 또는 내 정보 조회 API 호출
    const res = http.get(
        `${BASE_URL}/api/v1/users/me`,
        {
            headers: {
                Authorization: `Bearer ${user.accessToken}`,
                'Content-Type': 'application/json',
            },
            tags: {
                name: 'jwt_authenticated_request',
            },
        }
    );

    const isSuccess = res.status === 200;
    jwtAuthSuccessRate.add(isSuccess ? 1 : 0);
    jwtAuthServerErrorRate.add(res.status >= 500 ? 1 : 0);

    if (isSuccess) {
        successCounter.add(1);
    } else if (res.status >= 400 && res.status < 500) {
        clientErrorCounter.add(1);
    }

    check(res, {
        'status is 200 OK': (r) => r.status === 200,
    });
}
