import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {renderHook, waitFor} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import type {ReactNode} from "react";
import {beforeEach, describe, expect, it} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {ticketKeys} from "@/api/query-keys/tickets";
import {useTickets} from "@/hooks/use-tickets";
import {server} from "@/test/mocks/server";

function wrapper({children}: { children: ReactNode }) {
    const queryClient = new QueryClient({
        defaultOptions: {queries: {retry: false}},
    });

    return (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
}

function createWrapper(queryClient: QueryClient) {
    return function Wrapper({children}: { children: ReactNode }) {
        return (
            <QueryClientProvider client={queryClient}>
                {children}
            </QueryClientProvider>
        );
    };
}

describe("useTickets", () => {
    beforeEach(() => {
        sessionStorage.clear();
    });

    it("서버 티켓이 없으면 브라우저의 과거 MOCK 티켓 대신 빈 목록을 반환한다", async () => {
        sessionStorage.setItem(
            "completedMockTickets",
            JSON.stringify([{ticketId: -1, ticketNo: "MOCK-1"}]),
        );
        server.use(
            http.get(`${API_BASE_URL}/tickets`, () =>
                HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "티켓 목록 조회 성공",
                    data: {
                        content: [],
                        pageNumber: 0,
                        pageSize: 100,
                        totalElements: 0,
                        totalPages: 0,
                        isFirst: true,
                        isLast: true,
                    },
                }),
            ),
        );

        const {result} = renderHook(() => useTickets(true), {wrapper});

        await waitFor(() => expect(result.current.isSuccess).toBe(true));
        expect(result.current.data).toEqual([]);
    });

    it("서버 재조회 결과로 승인 직후 캐시의 티켓 상태를 교체한다", async () => {
        const queryClient = new QueryClient({
            defaultOptions: {queries: {retry: false}},
        });
        const issuedTicket = {
            ticketId: 1,
            ticketNo: "TKT-1",
            gameId: 10,
            seat: "1루 101-A-1",
            status: "ISSUED" as const,
            qrToken: "qr-1",
            gameAt: "2026-08-30T18:30:00",
        };
        queryClient.setQueryData(ticketKeys.list(), [issuedTicket]);
        server.use(
            http.get(`${API_BASE_URL}/tickets`, () =>
                HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "티켓 목록 조회 성공",
                    data: {
                        content: [{...issuedTicket, status: "USED"}],
                        pageNumber: 0,
                        pageSize: 100,
                        totalElements: 1,
                        totalPages: 1,
                        isFirst: true,
                        isLast: true,
                    },
                }),
            ),
        );

        const {result} = renderHook(() => useTickets(true), {
            wrapper: createWrapper(queryClient),
        });

        await waitFor(() => {
            expect(result.current.data?.[0].status).toBe("USED");
        });
    });
});
