import {
  API_BASE_URL,
  AppError,
  refreshAccessToken,
} from "@/api/client";
import { storage } from "@/lib/storage";

/**
 * 인증 헤더를 포함해 SSE 연결을 연다.
 *
 * 브라우저의 기본 EventSource는 Authorization 같은 사용자 정의 헤더를
 * 전달하기 어렵기 때문에 fetch를 사용한다. allowRefresh는 만료된 토큰으로
 * 연결이 계속 반복되지 않도록 재발급과 재연결을 최초 한 번으로 제한한다.
 */
async function openSse(
  path: string,
  signal: AbortSignal,
  allowRefresh: boolean,
) {
  // 서버에 일반 JSON 응답이 아니라 SSE 스트림을 요청한다고 알린다.
  const headers = new Headers({ Accept: "text/event-stream" });
  const accessToken = storage.local.get("accessToken");

  // 대기열 스트림도 인증이 필요한 API이므로 현재 access token을 함께 전송한다.
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { headers, signal });

  // 연결 시점에 토큰이 만료됐다면 공통 재발급 작업을 기다린 뒤 새 토큰으로 다시 연결한다.
  if (
    response.status === 401 &&
    allowRefresh &&
    (await refreshAccessToken())
  ) {
    // false를 넘겨 재연결도 401일 때 다시 재발급하는 무한 반복을 막는다.
    return openSse(path, signal, false);
  }

  return response;
}

/**
 * SSE 응답 스트림을 읽어 완성된 이벤트 이름과 JSON 데이터를 전달한다.
 *
 * 네트워크 chunk는 SSE 이벤트 경계와 일치하지 않을 수 있다. 따라서 수신한
 * 문자열을 buffer에 누적하고, 빈 줄로 끝난 이벤트만 파싱해 onEvent를 호출한다.
 * signal이 중단되면 진행 중인 fetch와 스트림 읽기도 함께 종료된다.
 */
export async function streamSse(
  path: string,
  onEvent: (event: string, data: unknown) => void,
  signal: AbortSignal,
) {
  const response = await openSse(path, signal, true);

  if (!response.ok || !response.body) {
    throw new AppError("대기열 실시간 연결에 실패했습니다.", response.status);
  }

  // response.body는 완성된 JSON이 아니라 계속 도착하는 바이트 스트림이다.
  const reader = response.body.getReader();

  // TextDecoder는 Uint8Array 형태의 네트워크 바이트를 JavaScript 문자열로 바꾼다.
  const decoder = new TextDecoder();

  // 아직 빈 줄까지 도착하지 않아 완성되지 않은 이벤트 조각을 보관한다.
  let buffer = "";

  while (true) {
    // reader.read()는 현재 도착한 바이트 조각과 스트림 종료 여부를 반환한다.
    const { value, done } = await reader.read();
    if (done) break;

    // 1. 네트워크 chunk는 SSE 한 줄의 중간에서도 끊길 수 있으므로 문자열을 누적한다.
    buffer += decoder.decode(value, { stream: true });

    // 2. 빈 줄은 하나의 SSE 이벤트가 끝났다는 뜻이다.
    const blocks = buffer.split(/\r?\n\r?\n/);

    // 3. 마지막 조각은 아직 덜 수신된 이벤트일 수 있으므로 다음 chunk까지 보관한다.
    buffer = blocks.pop() ?? "";

    blocks.forEach((block) => {
      // event: 줄이 없으면 SSE 표준의 기본 이벤트 이름인 message를 사용한다.
      let eventName = "message";

      // 한 이벤트에 data: 줄이 여러 개 올 수 있으므로 모두 모은 뒤 합친다.
      const dataLines: string[] = [];

      block.split(/\r?\n/).forEach((line) => {
        if (line.startsWith("event:")) {
          // "event: rank"에서 접두어를 제거하고 "rank"만 남긴다.
          eventName = line.slice(6).trim();
        }
        if (line.startsWith("data:")) {
          // "data: {...}"에서 JSON 문자열 부분만 순서대로 수집한다.
          dataLines.push(line.slice(5).trim());
        }
      });

      if (dataLines.length > 0) {
        // SSE 규약의 여러 data 줄은 줄바꿈으로 이어 붙인 뒤 하나의 JSON으로 해석한다.
        onEvent(eventName, JSON.parse(dataLines.join("\n")));
      }
    });
  }
}
