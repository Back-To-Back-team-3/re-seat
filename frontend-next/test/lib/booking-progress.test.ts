import {beforeEach, describe, expect, it} from "vitest";

import {
    clearBookingProgress,
    loadBookingProgress,
    saveBookingProgress,
} from "@/lib/booking-progress";
import type {BookingData} from "@/stores/booking-store";

const progress: BookingData = {
    selectedGameId: 111,
    selectedZoneId: 10,
    selectedSeats: [],
    reservation: null,
    orderId: 200,
    paymentId: null,
    queueTokenExpiresAt: "2026-09-10 20:00:00",
    firstHoldExpiresAt: null,
};

describe("예매 진행 상태 저장소", () => {
    beforeEach(() => {
        sessionStorage.clear();
    });

    it("현재 버전의 예매 진행 상태를 저장하고 복원한다", () => {
        saveBookingProgress(progress);

        expect(loadBookingProgress()).toEqual(progress);
    });

    it("알 수 없는 버전의 저장값은 복원하지 않는다", () => {
        sessionStorage.setItem(
            "bookingProgress",
            JSON.stringify({version: 0, data: progress}),
        );

        expect(loadBookingProgress()).toBeNull();
    });

    it("기존 세션의 예약에서 최초 재선점 기한을 복원한다", () => {
        saveBookingProgress({
            ...progress,
            firstHoldExpiresAt: undefined,
            reservation: {
                reservationId: 10,
                reservationNo: "RES-10",
                status: "HOLDING",
                gameSeats: [],
                holdExpiresAt: "2026-09-10 20:10:00",
                gameAt: "2026-09-15 18:30:00",
            },
        });

        expect(loadBookingProgress()).toMatchObject({
            firstHoldExpiresAt: "2026-09-10 20:10:00",
            queueTokenExpiresAt: "2026-09-10 20:10:00",
        });
    });

    it("진행 상태를 초기화하면 저장값도 제거한다", () => {
        saveBookingProgress(progress);

        clearBookingProgress();

        expect(loadBookingProgress()).toBeNull();
    });
});
