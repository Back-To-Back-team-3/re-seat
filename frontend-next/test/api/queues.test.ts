import {beforeEach, describe, expect, it, vi} from "vitest";

import {streamQueue} from "@/api/queues";
import {streamSse} from "@/api/sse";

vi.mock("@/api/sse", () => ({
    streamSse: vi.fn(),
}));

describe("대기열 API", () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it("SSE 거절 이벤트를 거절 핸들러에 전달한다", async () => {
        const onReject = vi.fn();
        const rejection = {
            rejected: true as const,
            reason: "WAITING_IN_OTHER_GAME" as const,
        };
        vi.mocked(streamSse).mockImplementation(
            async (_path, onEvent) => onEvent("reject", rejection),
        );

        await streamQueue(
            111,
            {onRank: vi.fn(), onAdmit: vi.fn(), onReject},
            new AbortController().signal,
        );

        expect(onReject).toHaveBeenCalledWith(rejection);
    });
});
