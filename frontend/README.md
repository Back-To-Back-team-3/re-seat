# Re-Seat Frontend MVP

React + Vite + TypeScript 기반 MVP 화면이다.

## 실행

```bash
cd frontend
npm install
npm run dev
```

기본 API 주소:

```text
http://localhost:8080/api/v1
```

내 티켓 화면은 `GET /api/v1/tickets`를 우선 사용하며 로그인이 필요하다. 티켓 발급 연동 전까지 API 결과가 비어 있고 현재 브라우저 세션에 결제 완료 정보가 있으면 해당 정보로 만든 MOCK
티켓을 표시한다.
