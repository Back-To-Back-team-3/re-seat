import {http, HttpResponse} from "msw";
import {expect, it, vi} from "vitest";

import {streamSse} from "@/api/sse";
import {server} from "@/test/mocks/server";

const API_BASE_URL = "http://localhost:8080/api/v1";

it("분할 수신된 event와 여러 data 줄을 하나의 SSE 이벤트로 전달한다", async () => {
    // 실제 네트워크 응답처럼 문자열을 Uint8Array 바이트로 변환한다.
    const encoder = new TextEncoder();

    server.use(
        http.get(`${API_BASE_URL}/queues/1/stream`, () => {
            // 하나의 rank 이벤트를 event 이름과 JSON 줄 중간에서 의도적으로 나눈다.
            // streamSse가 각 chunk를 별도 이벤트로 오해하지 않고 buffer에서 복원해야 한다.
            const body = new ReadableStream({
                start(controller) {
                    controller.enqueue(encoder.encode("event: ra"));
                    controller.enqueue(encoder.encode('nk\ndata: {"rank": 3,'));
                    controller.enqueue(
                        encoder.encode('\ndata: "estimatedWaitSeconds": 10}\n\n'),
                    );

                    // 준비한 SSE 이벤트를 모두 보냈으므로 테스트용 스트림을 종료한다.
                    controller.close();
                },
            });

            return new HttpResponse(body, {
                headers: {"Content-Type": "text/event-stream"},
            });
        }),
    );

    // 파싱된 이벤트 이름과 데이터를 전달받을 호출자 역할을 한다.
    const onEvent = vi.fn();

    await streamSse("/queues/1/stream", onEvent, new AbortController().signal);

    // 세 개의 네트워크 chunk가 하나의 완성된 rank 이벤트로 전달되어야 한다.
    expect(onEvent).toHaveBeenCalledOnce();
    expect(onEvent).toHaveBeenCalledWith("rank", {
        rank: 3,
        estimatedWaitSeconds: 10,
    });
});
