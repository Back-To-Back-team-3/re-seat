# Re:Seat Next.js Frontend

기존 `frontend/` Vite 앱을 기준선으로 유지하면서 기능을 단계적으로 이전하는 Next.js 애플리케이션입니다.

## 로컬 실행

```bash
npm install
npm run dev
```

- Next.js: `http://localhost:5173`
- 기존 Vite 앱: `http://localhost:5173`
- Backend: `http://localhost:8080`

환경 변수는 `.env.example`을 참고해 `.env`에 설정합니다.

Next.js와 기존 Vite 앱은 같은 포트를 사용하므로 동시에 실행할 수 없습니다. 기능을 비교할 때는 현재 실행 중인 프론트엔드 서버를 종료한 뒤 다른 앱을 실행합니다.

## 검증

```bash
npm run lint
npm run build
```

기능 동등성 확인이 끝날 때까지 기존 `frontend/`를 수정하거나 삭제하지 않습니다.
