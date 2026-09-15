import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {act, renderHook} from "@testing-library/react";
import type {ReactNode} from "react";
import {beforeEach, describe, expect, it, vi} from "vitest";

import {cancelReservation, createReservation, getReservationHoldTime} from "@/api/reservations";
import {useReservation} from "@/hooks/use-reservation";
import type {ReservationResponse} from "@/types/reservation";

const bookingState = vi.hoisted(() => ({
    selectedSeats: [{gameSeatId: 101}],
    reservation: null as ReservationResponse | null,
    setReservation: vi.fn(),
    clearSeats: vi.fn(),
    setQueueExpiry: vi.fn(),
    setFirstHoldExpiry: vi.fn(),
}));

vi.mock("@/api/reservations", () => ({
    cancelReservation: vi.fn(),
    createReservation: vi.fn(),
    getReservationHoldTime: vi.fn(),
}));

vi.mock("@/providers/booking-store-provider", () => ({
    useBookingStore: (
        selector: (state: typeof bookingState) => unknown,
    ) => selector(bookingState),
}));

function createWrapper(queryClient: QueryClient) {
    return function Wrapper({children}: { children: ReactNode }) {
        return (
            <QueryClientProvider client={queryClient}>
                {children}
            </QueryClientProvider>
        );
    };
}

describe("useReservation", () => {
    beforeEach(() => {
        localStorage.clear();
        bookingState.setReservation.mockClear();
        bookingState.clearSeats.mockClear();
        bookingState.setQueueExpiry.mockClear();
        bookingState.setFirstHoldExpiry.mockClear();
        bookingState.reservation = null;
        vi.mocked(cancelReservation).mockResolvedValue({
            reservationId: 10,
            status: "CANCELED",
        });
        vi.mocked(createReservation).mockResolvedValue({
            reservationId: 10,
            reservationNo: "RES-10",
            status: "HOLDING",
            gameSeats: [{gameSeatId: 101, status: "HELD", price: 18000}],
            holdExpiresAt: "2026-09-10T18:40:00",
            gameAt: "2026-09-10T18:30:00",
        });
        vi.mocked(getReservationHoldTime).mockResolvedValue({
            reservationId: 10,
            remainingSeconds: 600,
            status: "HOLDING",
            expiresAt: "2026-09-10T18:40:00",
        });
    });

    it("좌석 선점에 성공해도 결제 결과 전까지 대기열 토큰을 유지한다", async () => {
        const queryClient = new QueryClient({
            defaultOptions: {
                queries: {retry: false},
                mutations: {retry: false},
            },
        });
        localStorage.setItem("queueToken", "queue-token");

        const {result} = renderHook(() => useReservation(40), {
            wrapper: createWrapper(queryClient),
        });

        await act(async () => {
            await result.current.create.mutateAsync();
        });

        expect(localStorage.getItem("queueToken")).toBe("queue-token");
        expect(bookingState.setQueueExpiry).not.toHaveBeenCalledWith(null);
        expect(bookingState.setFirstHoldExpiry).toHaveBeenCalledWith("2026-09-10T18:40:00");
    });

    it("좌석 선점을 해제해도 입장 토큰은 유지한다", async () => {
        const queryClient = new QueryClient({
            defaultOptions: {
                queries: {retry: false},
                mutations: {retry: false},
            },
        });
        bookingState.reservation = {
            reservationId: 10,
            reservationNo: "RES-10",
            status: "HOLDING",
            gameSeats: [{gameSeatId: 101, status: "HELD", price: 18000}],
            holdExpiresAt: "2026-09-10T18:40:00",
            gameAt: "2026-09-10T18:30:00",
        };
        localStorage.setItem("queueToken", "queue-token");

        const {result} = renderHook(() => useReservation(40), {
            wrapper: createWrapper(queryClient),
        });

        await act(async () => {
            await result.current.cancel.mutateAsync();
        });

        expect(cancelReservation).toHaveBeenCalledWith(10);
        expect(localStorage.getItem("queueToken")).toBe("queue-token");
        expect(bookingState.setQueueExpiry).not.toHaveBeenCalledWith(null);
        expect(bookingState.setReservation).toHaveBeenCalledWith(null);
        expect(bookingState.clearSeats).toHaveBeenCalledOnce();
    });
});
