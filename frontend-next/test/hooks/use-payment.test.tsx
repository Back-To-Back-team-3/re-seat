import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {renderHook, waitFor} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import type {ReactNode} from "react";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {ticketKeys} from "@/api/query-keys/tickets";
import {usePayment} from "@/hooks/use-payment";
import {savePendingPayment} from "@/lib/payment-storage";
import {server} from "@/test/mocks/server";
import type {PaymentCreateResponse} from "@/types/payment";
import type {TicketSummary} from "@/types/ticket";

const bookingState = vi.hoisted(() => ({
    selectedGameId: 40 as number | null,
    setPaymentId: vi.fn(),
}));

vi.mock("@/providers/booking-store-provider", () => ({
    useBookingStore: (
        selector: (state: typeof bookingState) => unknown,
    ) => selector(bookingState),
}));

const oldTicket: TicketSummary = {
    ticketId: 1,
    ticketNo: "TKT-OLD",
    gameId: 39,
    seat: "3루 301-C-1",
    status: "ISSUED",
    qrToken: "qr-old",
    gameAt: "2026-09-01T18:30:00",
};

const cachedDuplicate: TicketSummary = {
    ticketId: 2,
    ticketNo: "TKT-DUPLICATE",
    gameId: 40,
    seat: "이전 좌석 표기",
    status: "ISSUED",
    qrToken: "old-qr",
    gameAt: "2026-08-30T18:30:00",
};

const approvedDuplicate: TicketSummary = {
    ...cachedDuplicate,
    seat: "1루 101-A-1",
    qrToken: "new-qr",
};

const newTicket: TicketSummary = {
    ticketId: 3,
    ticketNo: "TKT-NEW",
    gameId: 40,
    seat: "1루 101-A-2",
    status: "ISSUED",
    qrToken: "qr-new",
    gameAt: "2026-08-30T18:30:00",
};

const readyPayment: PaymentCreateResponse = {
    paymentId: 10,
    paymentNo: "PAY-10",
    orderId: 20,
    amount: 36000,
    method: null,
    status: "READY",
    pgProvider: "TOSS",
    pgOrderId: "PG-20",
    paymentDeadline: "2026-08-30 18:40:00",
};

function createWrapper(queryClient: QueryClient) {
    return function Wrapper({children}: { children: ReactNode }) {
        return (
            <QueryClientProvider client={queryClient}>
                {children}
            </QueryClientProvider>
        );
    };
}

describe("usePayment", () => {
    beforeEach(() => {
        localStorage.clear();
        sessionStorage.clear();
        bookingState.setPaymentId.mockClear();
        window.history.replaceState({}, "", "/payments/10");
    });

    afterEach(() => {
        window.history.replaceState({}, "", "/");
    });

    it("결제 승인 응답의 티켓을 기존 캐시에 ID 기준으로 병합한다", async () => {
        const queryClient = new QueryClient({
            defaultOptions: {
                queries: {retry: false},
                mutations: {retry: false},
            },
        });
        queryClient.setQueryData(ticketKeys.list(), [
            oldTicket,
            cachedDuplicate,
        ]);
        savePendingPayment({
            orderId: 20,
            gameId: 40,
            payment: readyPayment,
            idempotencyKey: "idempotency-key",
        });
        window.history.replaceState(
            {},
            "",
            "/payments/10?paymentKey=pg-key&orderId=PG-20&amount=36000",
        );
        server.use(
            http.get(`${API_BASE_URL}/payments/10`, () =>
                HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "결제 조회 성공",
                    data: {
                        paymentId: 10,
                        paymentNo: "PAY-10",
                        orderId: 20,
                        amount: 36000,
                        method: null,
                        status: "READY",
                        pgProvider: "TOSS",
                        failReason: null,
                        approvedAt: null,
                        failedAt: null,
                    },
                }),
            ),
            http.post(`${API_BASE_URL}/payments/10/complete`, () =>
                HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "결제 승인 완료",
                    data: {
                        paymentId: 10,
                        paymentNo: "PAY-10",
                        status: "APPROVED",
                        method: "간편결제",
                        orderId: 20,
                        orderStatus: "PAID",
                        tickets: [approvedDuplicate, newTicket],
                    },
                }),
            ),
        );

        renderHook(() => usePayment(10), {
            wrapper: createWrapper(queryClient),
        });

        await waitFor(() => {
            expect(queryClient.getQueryData(ticketKeys.list())).toEqual([
                oldTicket,
                newTicket,
                approvedDuplicate,
            ]);
        });
        expect(queryClient.getQueryState(ticketKeys.list())?.isInvalidated).toBe(
            false,
        );
    });
});
