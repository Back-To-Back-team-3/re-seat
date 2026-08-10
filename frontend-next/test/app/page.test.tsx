import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {cleanup, render, screen, waitFor} from "@testing-library/react";
import {http, HttpResponse} from "msw";
import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";

import Home from "@/app/page";
import {clearAuthNotice} from "@/api/auth";
import {API_BASE_URL} from "@/api/client";
import {server} from "@/test/mocks/server";

vi.mock("next/navigation", () => ({
    useRouter: () => ({push: vi.fn()}),
}));

function renderHome() {
    // 테스트마다 독립된 QueryClient를 사용해 이전 테스트의 프로필 캐시가 섞이지 않게 한다.
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: {retry: false},
            mutations: {retry: false},
        },
    });

    return render(
        <QueryClientProvider client={queryClient}>
            <Home/>
        </QueryClientProvider>,
    );
}

function profileResponse(isVerified: boolean) {
    return {
        success: true,
        errorCode: null,
        message: "내 정보 조회 완료",
        data: {
            id: 1,
            email: "user@example.com",
            name: "테스트 사용자",
            nickname: "야구팬",
            phone: null,
            isVerified,
        },
    };
}

describe("인증과 사용자 프로필 흐름", () => {
    beforeEach(() => {
        localStorage.clear();
        clearAuthNotice();
        window.history.replaceState({}, "", "/");
        server.use(
            // 홈 화면이 공개 경기 목록도 함께 조회하므로 인증 테스트에는 빈 정상 응답을 제공한다.
            http.get(`${API_BASE_URL}/games`, ({request}) => {
                const page = Number(new URL(request.url).searchParams.get("page"));
                return HttpResponse.json({
                    success: true,
                    errorCode: null,
                    message: "경기 목록 조회 성공",
                    data: {
                        content: [],
                        pageNumber: page,
                        pageSize: 100,
                        totalElements: 0,
                        totalPages: 1,
                        isFirst: true,
                        isLast: true,
                    },
                });
            }),
        );
    });

    afterEach(() => {
        cleanup();
    });

    // 로그인 버튼과 로그인/로그아웃 후 프로필 표시는 이제 모든 route가 공유하는
    // 상단 Header(components/layout/header.tsx)가 소유하며, 이 페이지의 렌더링
    // 트리에는 포함되지 않는다. 해당 UI 동작은 test/components/layout/header.test.tsx가
    // 검증한다. 이 파일에는 GamesPage 자신이 소유한 동작(토큰 저장, 쿼리 문자열
    // 정리 같은 페이지 레벨 부수효과와 Alert/VerificationPanel 분기)만 남긴다.

    it("OAuth 콜백 토큰을 저장하고 쿼리 문자열을 정리한다", async () => {
        server.use(
            http.get(`${API_BASE_URL}/users/me`, () =>
                HttpResponse.json(profileResponse(true)),
            ),
        );
        window.history.replaceState(
            {},
            "",
            "/?accessToken=access-token&refreshToken=refresh-token&isVerified=true",
        );

        renderHome();

        await waitFor(() => {
            expect(localStorage.getItem("accessToken")).toBe("access-token");
            expect(localStorage.getItem("refreshToken")).toBe("refresh-token");
            expect(localStorage.getItem("isVerified")).toBe("true");
            expect(window.location.search).toBe("");
        });
    });

    it("프로필 조회 오류를 공통 알림으로 표시한다", async () => {
        localStorage.setItem("accessToken", "access-token");
        server.use(
            http.get(`${API_BASE_URL}/users/me`, () =>
                HttpResponse.json(
                    {
                        success: false,
                        errorCode: "USER_NOT_FOUND",
                        message: "사용자 정보를 찾을 수 없습니다.",
                        data: null,
                    },
                    {status: 404},
                ),
            ),
        );

        renderHome();

        expect(
            await screen.findByText("사용자 정보를 찾을 수 없습니다."),
        ).toBeInTheDocument();
    });

    it("본인인증이 필요한 사용자는 인증 화면을 먼저 표시한다", async () => {
        localStorage.setItem("accessToken", "access-token");
        server.use(
            http.get(`${API_BASE_URL}/users/me`, () =>
                HttpResponse.json(profileResponse(false)),
            ),
        );

        renderHome();

        expect(
            await screen.findByRole("heading", {
                name: /안전한 예매를 위한/,
            }),
        ).toBeInTheDocument();
        expect(
            await screen.findByRole("button", {name: "본인인증 시작하기"}),
        ).toBeInTheDocument();
    });
});
