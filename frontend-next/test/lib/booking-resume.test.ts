import {describe, expect, it, vi} from "vitest";

import {resolveBookingResume} from "@/lib/booking-resume";
import type {BookingData} from "@/stores/booking-store";

const GAME_ID = 111;
const FUTURE = "2026-09-10T12:10:00+09:00";
const NOW = new Date("2026-09-10T12:00:00+09:00");

function progress(overrides: Partial<BookingData> = {}): BookingData {
    return {
        selectedGameId: GAME_ID,
        selectedZoneId: 10,
        selectedSeats: [],
        reservation: null,
        orderId: null,
        paymentId: null,
        queueTokenExpiresAt: null,
        ...overrides,
    };
}

function dependencies() {
    return {
        getPayment: vi.fn(),
        getOrder: vi.fn(),
        getReservationHoldTime: vi.fn(),
        queueToken: null as string | null,
        now: NOW,
    };
}

describe("resolveBookingResume", () => {
    it("진행 중인 결제가 있으면 하위 단계보다 결제 화면을 우선한다", async () => {
        const deps = dependencies();
        deps.getPayment.mockResolvedValue({status: "READY"});

        const result = await resolveBookingResume(
            progress({
                paymentId: 30,
                orderId: 20,
                reservation: {
                    reservationId: 10,
                    reservationNo: "R-10",
                    status: "HOLDING",
                    gameSeats: [],
                    holdExpiresAt: FUTURE,
                    gameAt: FUTURE,
                },
            }),
            GAME_ID,
            deps,
        );

        expect(result.destination).toBe("/payments/30");
        expect(deps.getOrder).not.toHaveBeenCalled();
        expect(deps.getReservationHoldTime).not.toHaveBeenCalled();
    });

    it("결제가 종결됐지만 주문이 진행 중이면 주문 화면으로 복원한다", async () => {
        const deps = dependencies();
        deps.getPayment.mockResolvedValue({status: "FAILED"});
        deps.getOrder.mockResolvedValue({status: "CREATED"});

        const result = await resolveBookingResume(
            progress({paymentId: 30, orderId: 20}),
            GAME_ID,
            deps,
        );

        expect(result.destination).toBe("/orders/20");
        expect(result.progress.paymentId).toBeNull();
    });

    it("결제가 완료된 예매는 마이페이지로 이동하고 진행 상태를 종료한다", async () => {
        const deps = dependencies();
        deps.getPayment.mockResolvedValue({status: "APPROVED"});

        const result = await resolveBookingResume(
            progress({paymentId: 30, orderId: 20}),
            GAME_ID,
            deps,
        );

        expect(result.destination).toBe("/mypage");
        expect(result.progress.selectedGameId).toBeNull();
        expect(result.progress.paymentId).toBeNull();
    });

    it("유효한 좌석 선점이 있으면 주문 전 단계로 복원한다", async () => {
        const deps = dependencies();
        deps.getReservationHoldTime.mockResolvedValue({
            status: "HOLDING",
            remainingSeconds: 300,
        });

        const result = await resolveBookingResume(
            progress({
                reservation: {
                    reservationId: 10,
                    reservationNo: "R-10",
                    status: "HOLDING",
                    gameSeats: [],
                    holdExpiresAt: FUTURE,
                    gameAt: FUTURE,
                },
            }),
            GAME_ID,
            deps,
        );

        expect(result.destination).toBe("/checkout");
    });

    it("유효한 입장 토큰이 있으면 새 대기열 등록 대신 좌석 화면으로 복원한다", async () => {
        const deps = dependencies();
        deps.queueToken = "queue-token";

        const result = await resolveBookingResume(
            progress({queueTokenExpiresAt: FUTURE}),
            GAME_ID,
            deps,
        );

        expect(result.destination).toBe("/games/111/seats");
    });

    it("다른 경기 또는 만료된 진행 상태는 현재 경기의 새 예매로 초기화한다", async () => {
        const deps = dependencies();
        deps.queueToken = "expired-token";

        const result = await resolveBookingResume(
            progress({
                selectedGameId: 222,
                orderId: 20,
                queueTokenExpiresAt: "2026-09-10T11:59:00+09:00",
            }),
            GAME_ID,
            deps,
        );

        expect(result.destination).toBeNull();
        expect(result.progress).toMatchObject({
            selectedGameId: GAME_ID,
            reservation: null,
            orderId: null,
            paymentId: null,
        });
    });
});
