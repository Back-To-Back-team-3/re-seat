import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// CityData (혼잡도 조회) 파트 부하 테스트 — 시나리오: 경기장별 혼잡도 동시/반복 조회
// 측정 목적: N명의 사용자가 실시간 경기장 혼잡도를 조회할 때 Cache Hit/Miss에 따른 응답시간과 서버 오류율 측정

// CityData 조회 결과용 메트릭 정의
const cityDataSuccessRate = new Rate('citydata_success_rate');
const cityDataServerErrorRate = new Rate('citydata_server_error_rate');
const successCounter = new Counter('citydata_success_count');
const clientErrorCounter = new Counter('citydata_client_error_count');
const unexpectedErrorCounter = new Counter('citydata_unexpected_error_count');

// 환경 변수 설정
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


// 부하 설정 (가상 사용자 수 및 테스트 기간)
const VUS = parseInt(__ENV.VUS || '50', 10);
const DURATION = __ENV.DURATION || '30s';

// 조회할 경기장 ID 범위 (기본값: 1 ~ 3번 경기장)
const STADIUM_ID_MIN = parseInt(__ENV.STADIUM_ID_MIN || '1', 10);
const STADIUM_ID_MAX = parseInt(__ENV.STADIUM_ID_MAX || '3', 10);

const loadStage = `${VUS}_vus`;

// Thresholds (임계값) 정의
const thresholds = {
    http_req_duration: ['p(99)<500'],             // Cache 적용 타겟이므로 p99 지연 500ms 이하
    citydata_server_error_rate: ['rate<0.01'],    // 5xx 서버 에러율 1% 미만
    citydata_success_rate: ['rate>0.99'],         // 200 OK 응답 성공률 99% 초과
};

thresholds[`http_req_duration{load_stage:${loadStage}}`] = ['p(99)<500'];
thresholds[`citydata_server_error_rate{load_stage:${loadStage}}`] = ['rate<0.01'];
thresholds[`citydata_success_rate{load_stage:${loadStage}}`] = ['rate>0.99'];

export const options = {
    scenarios: {
        citydata_load: {
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
    // 1~3번 경기장 중 랜덤 호출 (Cache Hit / Miss 교차 테스트)
    const stadiumId = Math.floor(Math.random() * (STADIUM_ID_MAX - STADIUM_ID_MIN + 1)) + STADIUM_ID_MIN;

    const res = http.get(
        `${BASE_URL}/api/v1/congestion/stadiums/${stadiumId}`,
        {
            headers: {
                'Content-Type': 'application/json',
            },
            responseCallback: http.expectedStatuses(200),
            tags: {
                name: 'citydata_congestion_read',
            },
        }
    );

    // 카운터 및 지표 집계
    const isSuccess = res.status === 200;
    cityDataSuccessRate.add(isSuccess ? 1 : 0);
    cityDataServerErrorRate.add(res.status >= 500 ? 1 : 0);

    if (isSuccess) {
        successCounter.add(1);
    } else if (res.status >= 400 && res.status < 500) {
        clientErrorCounter.add(1);
    } else {
        unexpectedErrorCounter.add(1);
    }

    // 응답 상태 검증
    check(res, {
        'citydata read is 200 OK': (result) => result.status === 200,
        'has valid response body': (result) => result.body && result.body.length > 0,
    });
}
