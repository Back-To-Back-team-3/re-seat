import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {act, renderHook, waitFor} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import type {ReactNode} from "react";
import {describe, expect, it} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {useAdminUserDetail, useAdminUsers} from "@/hooks/use-admin-users";
import {server} from "@/test/mocks/server";

function wrapper(queryClient: QueryClient) {
    function QueryWrapper({children}: {children: ReactNode}) {
        return (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
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

const user = {
    id: 7,
    email: "fan@example.com",
    name: "야구팬",
    nickname: "응원단장",
    phone: "010-1234-5678",
    role: "USER",
    status: "ACTIVE",
    verified: true,
    createdAt: "2026-09-01T10:00:00",
    updatedAt: "2026-09-01T10:00:00",
};

describe("관리자 회원 훅", () => {
    it("검색 조건과 페이지를 회원 목록 API에 전달한다", async () => {
        server.use(
            http.get(`${API_BASE_URL}/admin/users`, ({request}) => {
                const params = new URL(request.url).searchParams;
                expect(params.get("email")).toBe("fan@example.com");
                expect(params.get("status")).toBe("ACTIVE");
                expect(params.get("page")).toBe("2");

                return HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "회원 목록 조회 완료",
                    data: {
                        content: [user],
                        pageNumber: 2,
                        pageSize: 20,
                        totalElements: 41,
                        totalPages: 3,
                        isFirst: false,
                        isLast: true,
                    },
                });
            }),
        );

        const {result} = renderHook(
            () => useAdminUsers({email: "fan@example.com", status: "ACTIVE"}, 2),
            {wrapper: wrapper(createQueryClient())},
        );

        await waitFor(() => expect(result.current.data?.content).toHaveLength(1));
    });

    it("회원 역할 변경 후 상세 정보를 다시 조회한다", async () => {
        let role = "USER";
        let detailReads = 0;
        server.use(
            http.get(`${API_BASE_URL}/admin/users/7`, () => {
                detailReads += 1;
                return HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "회원 상세 조회 완료",
                    data: {...user, role},
                });
            }),
            http.get(`${API_BASE_URL}/admin/tickets/users/7`, () =>
                HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "사용자 티켓 조회 완료",
                    data: {
                        content: [],
                        pageNumber: 0,
                        pageSize: 20,
                        totalElements: 0,
                        totalPages: 0,
                        isFirst: true,
                        isLast: true,
                    },
                }),
            ),
            http.patch(`${API_BASE_URL}/admin/users/7/role`, async ({request}) => {
                role = ((await request.json()) as {role: string}).role;
                return HttpResponse.json({success: true, message: "변경 완료", data: null});
            }),
        );

        const {result} = renderHook(() => useAdminUserDetail(7), {
            wrapper: wrapper(createQueryClient()),
        });
        await waitFor(() => expect(result.current.user?.role).toBe("USER"));

        await act(async () => {
            await result.current.updateRole("ADMIN");
        });

        await waitFor(() => expect(result.current.user?.role).toBe("ADMIN"));
        expect(detailReads).toBeGreaterThan(1);
    });
});
