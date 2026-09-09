import {cleanup, fireEvent, render, screen, waitFor} from "@testing-library/react";
import {afterEach, beforeEach, describe, expect, it} from "vitest";

import {
    loadBookingProgress,
    saveBookingProgress,
} from "@/lib/booking-progress";
import {
    BookingStoreProvider,
    useBookingStore,
} from "@/providers/booking-store-provider";

function BookingStateProbe() {
    const hydrated = useBookingStore((state) => state.hydrated);
    const gameId = useBookingStore((state) => state.selectedGameId);
    const setGame = useBookingStore((state) => state.setGame);
    const reset = useBookingStore((state) => state.reset);

    return (
        <>
            <span>{hydrated ? "hydrated" : "pending"}</span>
            <span>{gameId ?? "none"}</span>
            <button onClick={() => setGame(222)} type="button">
                경기 변경
            </button>
            <button onClick={reset} type="button">
                초기화
            </button>
        </>
    );
}

describe("BookingStoreProvider", () => {
    beforeEach(() => {
        sessionStorage.clear();
    });

    afterEach(cleanup);

    it("마운트 후 세션의 예매 진행 상태를 복원한다", async () => {
        saveBookingProgress({
            selectedGameId: 111,
            selectedZoneId: null,
            selectedSeats: [],
            reservation: null,
            orderId: null,
            paymentId: null,
            queueTokenExpiresAt: null,
        });

        render(
            <BookingStoreProvider>
                <BookingStateProbe />
            </BookingStoreProvider>,
        );

        expect(await screen.findByText("hydrated")).toBeInTheDocument();
        expect(screen.getByText("111")).toBeInTheDocument();
    });

    it("복원 후 변경된 진행 상태를 세션에 저장하고 reset하면 제거한다", async () => {
        render(
            <BookingStoreProvider>
                <BookingStateProbe />
            </BookingStoreProvider>,
        );
        await screen.findByText("hydrated");

        fireEvent.click(screen.getByRole("button", {name: "경기 변경"}));
        await waitFor(() => {
            expect(loadBookingProgress()?.selectedGameId).toBe(222);
        });

        fireEvent.click(screen.getByRole("button", {name: "초기화"}));
        await waitFor(() => {
            expect(loadBookingProgress()).toBeNull();
        });
    });
});
