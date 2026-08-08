import "@testing-library/jest-dom/vitest";

import { afterAll, afterEach, beforeAll } from "vitest";

import { server } from "@/test/mocks/server";

beforeAll(() => {
  // 테스트에서 등록하지 않은 HTTP 요청은 실제 서버로 보내지 않고 즉시 실패시킨다.
  // URL 오타나 누락된 mock 응답이 조용히 통과하는 일을 막기 위한 설정이다.
  server.listen({ onUnhandledRequest: "error" });
});

afterEach(() => {
  // 각 테스트가 추가한 응답 규칙을 지워 다음 테스트의 결과에 영향을 주지 않게 한다.
  server.resetHandlers();
});

afterAll(() => {
  // 전체 테스트가 끝나면 MSW의 HTTP 요청 가로채기를 해제한다.
  server.close();
});
