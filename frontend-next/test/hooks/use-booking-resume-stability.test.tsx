import type {ReactNode} from "react";
import {renderHook, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";

import {useBookingResume} from "@/hooks/use-booking-resume";
import {BookingStoreProvider} from "@/providers/booking-store-provider";
import type {BookingData} from "@/stores/booking-store";

const mocks = vi.hoisted(() => ({
    resolveBookingResume: vi.fn(),
    router: {replace: vi.fn()},
}));

vi.mock("next/navigation", () => ({
    useRouter: () => mocks.router,
}));

vi.mock("@/lib/booking-resume", () => ({
    resolveBookingResume: (...args: unknown[]) =>
        mocks.resolveBookingResume(...args),
}));

const reconciledProgress = (): BookingData => ({
    selectedGameId: 111,
    selectedZoneId: null,
    selectedSeats: [],
    reservation: null,
    orderId: null,
    paymentId: null,
    queueTokenExpiresAt: null,
});

function wrapper({children}: { children: ReactNode }) {
    return <BookingStoreProvider>{children}</BookingStoreProvider>;
}

describe("useBookingResume 복원 안정성", () => {
    beforeEach(() => {
        localStorage.clear();
        sessionStorage.clear();
        vi.clearAllMocks();

        mocks.resolveBookingResume.mockImplementation(() => {
            // 회귀로 세 번째 호출이 발생해도 테스트가 무한 반복되지 않도록 대기 상태로 남긴다.
            if (mocks.resolveBookingResume.mock.calls.length >= 3) {
                return new Promise(() => undefined);
            }

            return Promise.resolve({
                destination: null,
                progress: reconciledProgress(),
            });
        });
    });

    it("내용이 같은 복원 결과를 다시 저장해도 복원 검사를 반복하지 않는다", async () => {
        const {result} = renderHook(() => useBookingResume(111), {wrapper});

        await waitFor(() => {
            expect(result.current.shouldEnterQueue).toBe(true);
        });
        await new Promise((resolve) => window.setTimeout(resolve, 20));

        expect(mocks.resolveBookingResume).toHaveBeenCalledTimes(2);
    });
});
