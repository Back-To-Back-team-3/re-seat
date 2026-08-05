# Vite·Next.js 동등성 비교

기존 Vite 앱은 마이그레이션의 동작·화면 기준선으로 사용합니다. 백엔드와 두
프론트엔드를 각각 별도 터미널에서 실행합니다.

```bash
# Backend: http://localhost:8080
./gradlew bootRun

# Vite: http://localhost:5173
cd frontend
npm run dev

# Next.js: http://localhost:3000
cd frontend-next
npm run dev
```

## 비교 원칙

- 경기 목록처럼 서버 상태를 바꾸지 않는 화면은 같은 viewport에서 함께 비교합니다.
- 대기열 진입, 좌석 선점, 예약, 주문, 결제는 두 앱에서 동시에 실행하지 않습니다.
- 상태 변경 흐름은 서로 겹치지 않는 테스트 사용자와 좌석 데이터가 준비된 경우에만
  앱별로 순차 실행합니다.
- 준비된 데이터가 없으면 자동 초기화를 가정하지 않고 mock 검증과 실제 연동 수동
  체크리스트를 분리합니다.
- 취소 가능한 예약과 주문은 기존 취소 API로 정리하고, 결제는 PG sandbox 또는
  MOCK provider에서만 비교합니다.
