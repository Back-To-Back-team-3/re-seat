import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {act, renderHook, waitFor} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import type {ReactNode} from "react";
import {beforeEach, describe, expect, it} from "vitest";

import {API_BASE_URL} from "@/api/client";
import {useAuth} from "@/hooks/use-auth";
import {server} from "@/test/mocks/server";

function createWrapper(queryClient: QueryClient) {
    return function Wrapper({children}: { children: ReactNode }) {
        return (
            <QueryClientProvider client={queryClient}>
                {children}
            </QueryClientProvider>
        );
    };
}

describe("useAuth", () => {
    beforeEach(() => {
        localStorage.clear();
        localStorage.setItem("accessToken", "access-token");
    });

    it("회원정보 수정 후 서버의 최신 프로필을 다시 조회한다", async () => {
        const queryClient = new QueryClient({
            defaultOptions: {
                queries: {retry: false},
                mutations: {retry: false},
            },
        });
        let profile = {
            id: 1,
            email: "user@example.com",
            name: "기존 이름",
            nickname: "야구팬",
            phone: "010-1234-5678",
            verified: true,
        };

        server.use(
            http.get(`${API_BASE_URL}/users/me`, () =>
                HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "내 정보 조회 완료",
                    data: profile,
                }),
            ),
            http.put(`${API_BASE_URL}/users/me`, async ({request}) => {
                const update = await request.json() as {
                    name: string;
                    phone: string;
                };
                profile = {...profile, ...update};

                return HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "회원정보 수정 완료",
                    data: null,
                });
            }),
        );

        const {result} = renderHook(() => useAuth(), {
            wrapper: createWrapper(queryClient),
        });

        await waitFor(() => {
            expect(result.current.profile?.name).toBe("기존 이름");
        });

        await act(async () => {
            await result.current.updateProfile({
                name: "새 이름",
                phone: "010-9876-5432",
            });
        });

        await waitFor(() => {
            expect(result.current.profile).toMatchObject({
                name: "새 이름",
                phone: "010-9876-5432",
            });
        });
    });
});
