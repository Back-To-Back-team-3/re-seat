import http from 'k6/http';
import {check, sleep} from 'k6';

// K6 공통 부하 테스트 템플릿 뼈대
// TODO: VUs(Virtual Users), duration, threshold(지연시간 p95, 에러율 failure rate) 설정 및 API 호출 시나리오 작성

export const options = {
	// TODO: 부하 시나리오 옵션 (stages, thresholds 등) 작성 위치
};

export default function () {
	// TODO: 대상 API 엔드포인트 호출 및 check() 로직 작성 위치
}
