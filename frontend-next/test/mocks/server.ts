import { setupServer } from "msw/node";

// 실제 포트를 열지 않고 Vitest 프로세스에서 발생한 HTTP 요청만 가로채는 공통 mock 서버다.
// 각 테스트는 server.use(...)로 해당 시나리오에 필요한 응답만 등록한다.
export const server = setupServer();
