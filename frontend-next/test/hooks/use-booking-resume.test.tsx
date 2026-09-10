import {renderHook, waitFor} from "@testing-library/react";
import {beforeEach, describe, expect, it, vi} from "vitest";

import {getPayment} from "@/api/payments";
import {useBookingResume} from "@/hooks/use-booking-resume";
import type {BookingState} from "@/stores/booking-store";

const mocks = vi.hoisted(() => ({
    router: {replace: vi.fn()},
    hydrate: vi.fn(),
    state: {
        hydrated: false,
        selectedGameId: 111,
        selectedZoneId: null,
        selectedSeats: [],
        reservation: null,
        orderId: null,
        paymentId: null,
        queueTokenExpiresAt: null,
    },
}));

vi.mock("next/navigation", () => ({
    useRouter: () => mocks.router,
}));

vi.mock("@/api/payments", () => ({getPayment: vi.fn()}));
vi.mock("@/api/orders", () => ({getOrder: vi.fn()}));
vi.mock("@/api/reservations", () => ({getReservationHoldTime: vi.fn()}));

vi.mock("@/providers/booking-store-provider", () => ({
    useBookingStore: (selector: (state: BookingState) => unknown) =>
        selector({
            ...mocks.state,
            hydrate: mocks.hydrate,
            setGame: vi.fn(),
            setZone: vi.fn(),
            toggleSeat: vi.fn(),
            clearSeats: vi.fn(),
            setReservation: vi.fn(),
            setOrderId: vi.fn(),
            setPaymentId: vi.fn(),
            setQueueExpiry: vi.fn(),
            reset: vi.fn(),
        } as BookingState),
}));

describe("useBookingResume", () => {
    beforeEach(() => {
        localStorage.clear();
        vi.clearAllMocks();
        Object.assign(mocks.state, {
            hydrated: false,
            selectedGameId: 111,
            selectedZoneId: null,
            selectedSeats: [],
            reservation: null,
            orderId: null,
            paymentId: null,
            queueTokenExpiresAt: null,
        });
    });

    it("세션 상태 복원이 끝나기 전에는 서버 확인과 새 큐 진입을 보류한다", () => {
        const {result} = renderHook(() => useBookingResume(111));

        expect(result.current.restoring).toBe(true);
        expect(result.current.shouldEnterQueue).toBe(false);
        expect(getPayment).not.toHaveBeenCalled();
        expect(mocks.router.replace).not.toHaveBeenCalled();
    });

    it("복원된 진행 상태를 서버에서 확인하고 가장 뒤쪽 단계로 이동한다", async () => {
        Object.assign(mocks.state, {hydrated: true, paymentId: 30});
        vi.mocked(getPayment).mockResolvedValue({status: "READY"} as never);

        const {result} = renderHook(() => useBookingResume(111));

        await waitFor(() => {
            expect(mocks.router.replace).toHaveBeenCalledWith("/payments/30");
        });
        expect(result.current.shouldEnterQueue).toBe(false);
        expect(mocks.hydrate).toHaveBeenCalled();
    });

    it("이어갈 서버 상태가 없을 때만 새 큐 진입을 허용한다", async () => {
        Object.assign(mocks.state, {hydrated: true});

        const {result} = renderHook(() => useBookingResume(111));

        await waitFor(() => {
            expect(result.current.shouldEnterQueue).toBe(true);
        });
        expect(mocks.router.replace).not.toHaveBeenCalled();
    });
});
