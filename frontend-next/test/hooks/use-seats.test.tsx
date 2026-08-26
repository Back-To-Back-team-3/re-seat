import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {renderHook, waitFor} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import type {ReactNode} from "react";
import {afterEach, beforeEach, describe, expect, it} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {useSeats} from "@/hooks/use-seats";
import {server} from "@/test/mocks/server";

const zones = [
    {
        zoneId: 101,
        zoneName: "101",
        grade: "INFIELD",
        basePrice: 18000,
        totalCount: 50,
        availableCount: 50,
    },
    {
        zoneId: 102,
        zoneName: "102",
        grade: "INFIELD",
        basePrice: 18000,
        totalCount: 50,
        availableCount: 50,
    },
];

function seatOf(zoneId: number) {
    return {
        gameSeatId: zoneId * 100,
        zoneId,
        zoneName: String(zoneId),
        grade: "INFIELD",
        seatBlock: "A",
        seatRow: "A",
        seatNumber: "1",
        price: 18000,
        status: "AVAILABLE",
    };
}

/** 좌석 조회에 실제로 사용된 요청 URL을 기록해 구역 없이 조회했는지 판별한다. */
let seatRequests: string[] = [];

function wrapper({children}: { children: ReactNode }) {
    const queryClient = new QueryClient({
        defaultOptions: {queries: {retry: false}},
    });

    return (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
}

describe("useSeats", () => {
    beforeEach(() => {
        seatRequests = [];
        server.use(
            http.get(`${API_BASE_URL}/games/1/zones`, () =>
                HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "구역 조회 성공",
                    data: zones,
                }),
            ),
            http.get(`${API_BASE_URL}/games/1/seats`, ({request}) => {
                const url = new URL(request.url);
                seatRequests.push(url.search);
                const zoneId = Number(url.searchParams.get("zoneId"));
                return HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "좌석 조회 성공",
                    data: [seatOf(zoneId || 0)],
                });
            }),
        );
    });

    afterEach(() => {
        seatRequests = [];
    });

    it("구역을 고르지 않아도 첫 구역의 좌석만 조회한다", async () => {
        const {result} = renderHook(() => useSeats(1, null), {wrapper});

        await waitFor(() => {
            expect(result.current.seats.data).toBeDefined();
        });

        expect(result.current.activeZoneId).toBe(101);
        // 구역 없이 조회하면 전 구역 좌석이 섞여 오므로 그런 요청이 없어야 한다.
        expect(seatRequests).toEqual(["?zoneId=101"]);
    });

    it("사용자가 고른 구역이 있으면 그 구역의 좌석을 조회한다", async () => {
        const {result} = renderHook(() => useSeats(1, 102), {wrapper});

        await waitFor(() => {
            expect(result.current.seats.data).toBeDefined();
        });

        expect(result.current.activeZoneId).toBe(102);
        expect(seatRequests).toEqual(["?zoneId=102"]);
    });
});
