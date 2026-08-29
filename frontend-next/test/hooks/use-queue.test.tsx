import {renderHook, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";

import {enterQueue, streamQueue} from "@/api/queues";
import {useQueue} from "@/hooks/use-queue";
import type {QueueAdmitEvent} from "@/types/game";

const mocks = vi.hoisted(() => ({
    router: {push: vi.fn()},
    setGame: vi.fn(),
    setQueueExpiry: vi.fn(),
}));

vi.mock("next/navigation", () => ({
    useRouter: () => mocks.router,
}));

vi.mock("@/api/queues", () => ({
    cancelQueue: vi.fn(),
    enterQueue: vi.fn(),
    streamQueue: vi.fn(),
}));

vi.mock("@/providers/booking-store-provider", () => ({
    useBookingStore: (
        selector: (state: {
            setGame: typeof mocks.setGame;
            setQueueExpiry: typeof mocks.setQueueExpiry;
        }) => unknown,
    ) =>
        selector({
            setGame: mocks.setGame,
            setQueueExpiry: mocks.setQueueExpiry,
        }),
}));

describe("useQueue", () => {
    beforeEach(() => {
        localStorage.clear();
        vi.clearAllMocks();
        vi.mocked(enterQueue).mockResolvedValue(undefined);
    });

    it("입장 허가 후 좌석 탐색 만료 시각을 좌석 화면 타이머에 저장한다", async () => {
        const tokenExpiresAt = "2026-08-29T15:21:00";
        const tokenSeatBrowsingExpiresAt = "2026-08-29T15:03:00";
        const admitEvent: QueueAdmitEvent = {
            admitted: true,
            queueToken: "queue-token",
            tokenExpiresAt,
            tokenSeatBrowsingExpiresAt,
        };

        vi.mocked(streamQueue).mockImplementation(async (_gameId, handlers) => {
            // 전체 토큰 TTL과 좌석 탐색 TTL이 함께 오는 실제 SSE admit 이벤트를 재현한다.
            handlers.onAdmit(admitEvent);
        });

        renderHook(() => useQueue(111));

        await waitFor(() => {
            expect(mocks.setQueueExpiry).toHaveBeenCalledWith(
                tokenSeatBrowsingExpiresAt,
            );
        });
        expect(localStorage.getItem("queueToken")).toBe("queue-token");
        expect(mocks.router.push).toHaveBeenCalledWith("/games/111/seats");
    });
});
