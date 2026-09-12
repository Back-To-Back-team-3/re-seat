import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '10s', target: 5 },  // 10초 동안 VU(가상 사용자) 5명까지 증가
        { duration: '20s', target: 10 }, // 20초 동안 10명 유지
        { duration: '5s', target: 0 },   // 5초 동안 종료
    ],
};

const BASE_URL = 'http://localhost:8080';
// Security 인증이 필요한 경우 아래에 JWT 토큰 입력 (Bearer 제외한 pure token 값)
const TOKEN = 'YOUR_JWT_TOKEN_HERE';

export default function () {
    // 테스트할 구장 ID 목록 (1번~3번 구장 순회 요청)
    const stadiumId = (Math.floor(Math.random() * 3) + 1);
    const url = `${BASE_URL}/api/v1/stadiums/${stadiumId}/congestion`;

    const params = {
        headers: {
            'Content-Type': 'application/json',
            // 'Authorization': `Bearer ${TOKEN}`, // Security 미적용 시 주석 처리
        },
    };

    const res = http.get(url, params);

    // 응답 검증 (200 OK 여부)
    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    sleep(0.5); // 0.5초 대기 후 다음 요청
}
