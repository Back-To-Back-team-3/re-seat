import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';

// =================================================================
// JWT 토큰 인증 부하 테스트 — Baseline 벤치마크 시나리오
// 측정 목적:
// 1. 대규모 동시 요청 환경에서 Spring Security JWT 인증 필터의 처리량(RPS) 및 지연시간(p95, p99) 측정
// 2. As-Is(기본 서명 검증) 상태의 기준치(Baseline)를 확보하여 향후 최적화(Caffeine 캐싱 등) 전후 비교 지표로 활용
// =================================================================

// 커스텀 메트릭 정의
const authSuccessRate = new Rate('jwt_auth_success_rate');
const authErrorRate = new Rate('jwt_auth_error_rate');
const authResponseTime = new Trend('jwt_auth_response_time', true);
const totalRequests = new Counter('jwt_auth_total_requests');

// =================================================================
// 1. 부하 시나리오 옵션 (Stages & Thresholds)
// =================================================================
export const options = {
    stages: [
        { duration: '20s', target: 50 },   // 1단계: Warm-up (50 VUs)
        { duration: '30s', target: 200 },  // 2단계: Ramp-up 1 (200 VUs)
        { duration: '1m',  target: 500 },  // 3단계: Peak Load (500 VUs 대규모 동시 요청)
        { duration: '30s', target: 500 },  // 4단계: Peak Load 유지
        { duration: '20s', target: 0 },    // 5단계: Cool-down (0 VUs)
    ],
    thresholds: {
        // 95%의 요청이 200ms 이하, 99%의 요청이 500ms 이내에 처리되어야 함
        http_req_duration: ['p(95)<200', 'p(99)<500'],
        // 인증 실패율 1% 미만 유지
        jwt_auth_error_rate: ['rate<0.01'],
    },
};

// =================================================================
// 2. 환경 변수 및 테스트 데이터 설정
// =================================================================
const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const SINGLE_JWT_TOKEN = __ENV.JWT_TOKEN || '';
const USER_DATA_PATH = __ENV.USER_DATA_PATH || '';

// 여러 사용자 토큰 파일이 지정된 경우 SharedArray로 로드, 없으면 빈 배열
let users = [];
if (USER_DATA_PATH) {
    try {
        users = new SharedArray('jwt benchmark users', () => JSON.parse(open(USER_DATA_PATH)));
    } catch (e) {
        console.warn(`사용자 토큰 파일 로드 실패: ${USER_DATA_PATH}, 단일 토큰 모드로 동작합니다.`);
    }
}

// =================================================================
// 3. 테스트 실행 로직 (VU Iteration)
// =================================================================
export default function () {
    totalRequests.add(1);

    // 사용자 토큰 결정: 다중 사용자 파일이 있으면 VU별 분산 사용, 없으면 단일 환경변수 토큰 사용
    let token = SINGLE_JWT_TOKEN;
    if (users && users.length > 0) {
        const userIndex = exec.scenario.iterationInTest % users.length;
        token = users[userIndex].accessToken || users[userIndex].token || SINGLE_JWT_TOKEN;
    }

    const params = {
        headers: {
            'Content-Type': 'application/json',
            ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        },
        // 200 이외의 에러 상태도 예외로 던지지 않고 응답 객체로 받아 검증
        responseCallback: http.expectedStatuses(200, 401, 403),
    };

    // DB 부하가 최소화된 인증 필수 엔드포인트(내 정보 조회) 호출
    const res = http.get(`${BASE_URL}/api/v1/users/me`, params);

    // 응답 상태 및 본문 검증
    const isSuccess = check(res, {
        'status is 200': (r) => r.status === 200,
        'has valid response body': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.success === true && body.data !== null;
            } catch (_) {
                return false;
            }
        },
    });

    // 메트릭 집계
    authSuccessRate.add(isSuccess);
    authErrorRate.add(!isSuccess);
    authResponseTime.add(res.timings.duration);

    // 실제 사용자의 연속 호출 간격 모사 (50ms)
    sleep(0.05);
}
