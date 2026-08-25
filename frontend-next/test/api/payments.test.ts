import {http, HttpResponse} from "msw";
import {describe, expect, it} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {
    completePayment,
    failPayment,
    requestPayment,
} from "@/api/payments";
import {server} from "@/test/mocks/server";

describe("결제 API", () => {
    it("결제 생성 응답에서 결제 기한을 반환한다", async () => {
        server.use(
            http.post(`${API_BASE_URL}/payments`, () =>
                HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "결제 생성 성공",
                    data: {
                        paymentId: 10,
                        paymentNo: "PAY-10",
                        orderId: 20,
                        amount: 18000,
                        method: null,
                        status: "READY",
                        pgProvider: "TOSS",
                        pgOrderId: "PG-20",
                        paymentDeadline: "2026-08-30 18:40:00",
                    },
                }),
            ),
        );

        const payment = await requestPayment(20, "idempotency-key");

        expect(payment.paymentDeadline).toBe("2026-08-30 18:40:00");
    });

    it("결제 승인 응답에서 주문 상태와 발급 티켓을 반환한다", async () => {
        server.use(
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
                        tickets: [
                            {
                                ticketId: 30,
                                ticketNo: "TKT-30",
                                gameId: 40,
                                seat: "1루 101-A-1",
                                status: "ISSUED",
                                qrToken: "qr-30",
                                gameAt: "2026-08-30T18:30:00",
                            },
                        ],
                    },
                }),
            ),
        );

        const result = await completePayment(10, "idempotency-key", {
            paymentKey: "pg-key",
            orderId: "PG-20",
            amount: 18000,
        });

        expect(result.orderStatus).toBe("PAID");
        expect(result.tickets[0].ticketId).toBe(30);
    });

    it("결제 실패 응답에서 실패 상태를 반환한다", async () => {
        server.use(
            http.post(`${API_BASE_URL}/payments/10/fail`, () =>
                HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "결제 실패 처리 완료",
                    data: {
                        paymentId: 10,
                        status: "FAILED",
                    },
                }),
            ),
        );

        const result = await failPayment(10, "idempotency-key", {
            code: "PAY_PROCESS_CANCELED",
            message: "사용자가 결제를 취소했습니다.",
            orderId: "PG-20",
        });

        expect(result).toEqual({paymentId: 10, status: "FAILED"});
    });
});
