import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {renderHook, waitFor} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import type {ReactNode} from "react";
import {beforeEach, describe, expect, it} from "vitest";

import {API_BASE_URL} from "@/api/client";
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
});
