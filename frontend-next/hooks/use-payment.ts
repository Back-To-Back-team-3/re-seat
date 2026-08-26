"use client";

import {useMutation, useQuery, useQueryClient} from "@tanstack/react-query";
import {useEffect, useRef} from "react";

import {completePayment, failPayment, getPayment, requestPayment,} from "@/api/payments";
import {orderKeys} from "@/api/query-keys/orders";
import {paymentKeys} from "@/api/query-keys/payments";
import {ticketKeys} from "@/api/query-keys/tickets";
import {rememberCompletedGame} from "@/lib/completed-games";
import {
    beginPaymentCallback,
    clearPendingPayment,
    completePaymentCallback,
    getPaymentKey,
    getPendingPayment,
    resetPaymentCallback,
} from "@/lib/payment-storage";
import {useBookingStore} from "@/providers/booking-store-provider";
import type {TicketSummary} from "@/types/ticket";

export function usePayment(paymentId?: number) {
    const queryClient = useQueryClient();
    const setPaymentId = useBookingStore((state) => state.setPaymentId);
    const selectedGameId = useBookingStore((state) => state.selectedGameId);
    const callbackStarted = useRef(false);
    const detail = useQuery({
        queryKey: paymentKeys.detail(paymentId ?? 0),
        queryFn: () => getPayment(paymentId!),
        enabled: Boolean(paymentId),
    });
    const prepare = useMutation({
        mutationFn: async (orderId: number) => {
            // 1. 같은 주문은 sessionStorage에 저장한 멱등키를 계속 재사용한다.
            const idempotencyKey = getPaymentKey(orderId);
            const payment = await requestPayment(orderId, idempotencyKey);
            return {payment, idempotencyKey};
        },
        onSuccess: ({payment}) => {
            setPaymentId(payment.paymentId);
            if (payment.status === "APPROVED") {
                rememberCompletedGame(selectedGameId);
            }
        },
    });

    useEffect(() => {
        if (detail.data?.status === "APPROVED") {
            rememberCompletedGame(selectedGameId);
        }
    }, [detail.data?.status, selectedGameId]);

    useEffect(() => {
        if (!paymentId || callbackStarted.current) return;
        const params = new URLSearchParams(window.location.search);
        const paymentKey = params.get("paymentKey");
        const pgOrderId = params.get("orderId");
        const amount = Number(params.get("amount"));
        const code = params.get("code");
        const message = params.get("message");
        const pending = getPendingPayment();
        if (!pending || (!paymentKey && !code)) return;
        callbackStarted.current = true;
        // 2. processing/completed 표식이 있으면 새로고침이나 StrictMode의 중복 콜백을 막는다.
        if (!beginPaymentCallback(paymentId)) return;

        async function processCallback() {
            try {
                if (paymentKey && pgOrderId) {
                    const action = await completePayment(
                        paymentId!,
                        pending!.idempotencyKey,
                        {
                            paymentKey,
                            orderId: pgOrderId,
                            amount,
                        },
                    );
                    if (action.status === "APPROVED") {
                        rememberCompletedGame(pending!.gameId);

                        /*
                         * 승인 응답에는 이번 결제로 발급된 실제 티켓이 포함됩니다.
                         * 기존 캐시의 과거 티켓은 유지하고 같은 ticketId는 최신 승인
                         * 응답으로 교체해 callback이 중복되어도 한 장만 남깁니다.
                         */
                        queryClient.setQueryData<TicketSummary[]>(
                            ticketKeys.list(),
                            (current = []) => {
                                const byId = new Map(
                                    [...current, ...action.tickets].map((ticket) => [
                                        ticket.ticketId,
                                        ticket,
                                    ]),
                                );

                                return [...byId.values()].sort(
                                    (left, right) =>
                                        right.gameAt.localeCompare(left.gameAt) ||
                                        right.ticketId - left.ticketId,
                                );
                            },
                        );
                    }
                } else if (code && message && pgOrderId) {
                    await failPayment(paymentId!, pending!.idempotencyKey, {
                        code,
                        message,
                        orderId: pgOrderId,
                    });
                }
                // 3. 성공한 콜백은 주문·결제 서버 상태를 명시적으로 다시 읽게 한다.
                // 티켓은 승인 응답을 바로 캐시에 저장했으므로 여기서 다시 요청하지 않는다.
                await Promise.all([
                    queryClient.invalidateQueries({
                        queryKey: orderKeys.detail(pending!.orderId),
                    }),
                    queryClient.invalidateQueries({
                        queryKey: paymentKeys.detail(paymentId!),
                    }),
                ]);
                completePaymentCallback(paymentId!);
                window.history.replaceState({}, "", window.location.pathname);
            } catch {
                // 4. 실패한 처리만 표식을 제거해 사용자가 같은 콜백을 다시 시도할 수 있게 한다.
                resetPaymentCallback(paymentId!);
            } finally {
                clearPendingPayment();
            }
        }

        void processCallback();
    }, [paymentId, queryClient]);

    return {detail, prepare};
}
