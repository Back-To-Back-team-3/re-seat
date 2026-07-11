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

## Mock fallback

`.env` 또는 `.env.local`에 아래 값을 둘 수 있다.

```env
VITE_USE_MOCK_FALLBACK=true
```

현재 백엔드 API가 없는 티켓 화면만 mock 데이터를 사용해 화면 흐름을 유지한다.
