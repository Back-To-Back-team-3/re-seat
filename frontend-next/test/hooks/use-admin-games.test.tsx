import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {act, renderHook, waitFor} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import type {ReactNode} from "react";
import {describe, expect, it, vi} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {useAdminGames} from "@/hooks/use-admin-games";
import {server} from "@/test/mocks/server";

function createWrapper(queryClient: QueryClient) {
    function QueryWrapper({children}: {children: ReactNode}) {
        return (
            <QueryClientProvider client={queryClient}>
                {children}
            </QueryClientProvider>
        );
    }

    return QueryWrapper;
}

function createQueryClient() {
    return new QueryClient({
        defaultOptions: {
            queries: {retry: false},
            mutations: {retry: false},
        },
    });
}

const game = {
    gameId: 111,
    title: "LG 트윈스 vs 두산 베어스",
    homeTeam: {teamId: 1, name: "LG 트윈스"},
    awayTeam: {teamId: 2, name: "두산 베어스"},
    stadium: {stadiumId: 1, name: "잠실야구장"},
    gameAt: "2026-09-12 18:30:00",
    bookingOpenAt: "2026-09-01 10:00:00",
    bookingCloseAt: "2026-09-12 17:30:00",
    bookingStatus: "OPEN",
};

function mockGameList() {
    server.use(
        http.get(`${API_BASE_URL}/games`, ({request}) => {
            // 전체 목록 훅은 예매 상태별 페이지를 합치므로 각 상태 요청에 맞춰 응답합니다.
            const status = new URL(request.url).searchParams.get("bookingStatus");
            return HttpResponse.json({
                success: true,
                errorCode: null,
                message: "경기 목록 조회 성공",
                data: {
                    content: status === "OPEN" ? [game] : [],
                    pageNumber: 0,
                    pageSize: 100,
                    totalElements: status === "OPEN" ? 1 : 0,
                    totalPages: 1,
                    isFirst: true,
                    isLast: true,
                },
            });
        }),
    );
}

describe("관리자 경기 훅", () => {
    it("예매 상태와 변경 사유를 관리자 API에 전달한다", async () => {
        const requestBody = vi.fn();
        mockGameList();
        server.use(
            http.patch(
                `${API_BASE_URL}/admin/games/111/booking-status`,
                async ({request}) => {
                    requestBody(await request.json());
                    return HttpResponse.json({
                        success: true,
                        errorCode: null,
                        message: "예매 상태 변경 완료",
                        data: {gameId: 111, bookingStatus: "CLOSED"},
                    });
                },
            ),
        );
        const {result} = renderHook(() => useAdminGames(), {
            wrapper: createWrapper(createQueryClient()),
        });
        await waitFor(() => expect(result.current.games).toHaveLength(1));

        await act(async () => {
            await result.current.updateStatus(111, "CLOSED", "판매 종료");
        });

        expect(requestBody).toHaveBeenCalledWith({
            bookingStatus: "CLOSED",
            reason: "판매 종료",
        });
    });

    it("선택한 경기의 좌석 재고 생성을 요청한다", async () => {
        const inventoryRequest = vi.fn();
        mockGameList();
        server.use(
            http.post(`${API_BASE_URL}/admin/games/111/seats`, () => {
                inventoryRequest();
                return HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "좌석 재고 생성 완료",
                    data: {
                        gameId: 111,
                        createdCount: 500,
                        priceRange: {min: 10000, max: 50000},
                    },
                });
            }),
        );
        const {result} = renderHook(() => useAdminGames(), {
            wrapper: createWrapper(createQueryClient()),
        });
        await waitFor(() => expect(result.current.games).toHaveLength(1));

        await act(async () => {
            await result.current.openInventory(111);
        });

        expect(inventoryRequest).toHaveBeenCalledOnce();
    });
});
